package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionStatus;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.service.DefaultExecutionService;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.harness.opencode.OpenCodeHarnessAdapter;
import eu.kalafatic.dstudio.infrastructure.config.DStudioPaths;
import eu.kalafatic.dstudio.infrastructure.git.JGitWorkspaceDiffService;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteExecutionStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteReviewStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeLiveExecutionTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private DefaultHarnessRegistry registry;
    private ExecutionService executionService;

    @BeforeEach
    void setUp() {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("live-exec.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("dstudio-home"));
        registry = new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                new SecretCodec(tempDir)));
        registry.registerAdapter(new OpenCodeHarnessAdapter(
                new JGitWorkspaceDiffService(), paths));
        executionService = new DefaultExecutionService(registry,
                new SqliteExecutionStore(databaseManager.connection()),
                new SqliteReviewStore(databaseManager.connection()),
                new JGitWorkspaceDiffService(), new DefaultEventBus());
    }

    @AfterEach
    void tearDown() {
        executionService.shutdown();
        databaseManager.close();
    }

    private Path gitWorkspace() throws Exception {
        Path repo = tempDir.resolve("live-workspace");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "# Live workspace\n");
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();
        }
        return repo;
    }

    private ExecutionTask awaitTerminal(String id) throws Exception {
        for (int i = 0; i < 600; i++) {
            ExecutionTask task = executionService.getTask(id).orElseThrow();
            if (task.isTerminal()) {
                return task;
            }
            Thread.sleep(500);
        }
        executionService.cancel(id);
        throw new AssertionError("Real OpenCode execution did not terminate in time");
    }

    @Test
    @Timeout(420)
    void realOpenCodeExecutionCreatesMarkerInsideWorkspace() throws Exception {
        if (!new OpenCodeHarnessAdapter(new JGitWorkspaceDiffService(),
                new DStudioPaths(tempDir.resolve("dstudio-home")))
                        .testConnection().success()) {
            return;
        }
        Path workspace = gitWorkspace();
        ExecutionTask submitted = null;
        String lastDiagnostic = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            String instruction = attempt == 0
                    ? "Create a new file named live-marker.txt in the current directory "
                            + "containing exactly the text: dstudio-live-ok. Then verify "
                            + "the file exists before finishing."
                    : "The file live-marker.txt must now contain exactly the text "
                            + "dstudio-live-ok. Create or rewrite live-marker.txt so that "
                            + "its full content is exactly: dstudio-live-ok";
            submitted = executionService.submit(
                    new ExecutionService.ExecutionRequest("opencode", null, null,
                            instruction, null, null, null, false,
                            workspace.toString(), Duration.ofMinutes(6)));
            ExecutionTask task = awaitTerminal(submitted.getId());
            lastDiagnostic = task.getErrorMessage() == null ? ""
                    : task.getErrorMessage();
            if (task.getStatus() == ExecutionStatus.COMPLETED
                    && containsMarker(workspace, "dstudio-live-ok")) {
                List<ExecutionEvent> events = executionService.getEvents(
                        submitted.getId(), 0, 500);
                assertTrue(events.stream().anyMatch(e ->
                        e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_LOG));
                assertTrue(events.stream().anyMatch(e ->
                        e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_COMPLETED));
                return;
            }
            System.out.println("[OpenCodeLive] attempt " + (attempt + 1)
                    + " did not produce marker (status " + task.getStatus()
                    + ", diagnostic: " + lastDiagnostic + ")");
        }
        throw new AssertionError("marker content not created in workspace after 2 attempts; "
                + "last status: " + executionService.getTask(submitted.getId())
                        .orElseThrow().getStatus()
                + ", diagnostic: " + lastDiagnostic);
    }

    private boolean containsMarker(Path root, String marker) {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> !p.toString().contains(".git"))
                    .filter(Files::isRegularFile)
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains(marker);
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @Timeout(420)
    void invalidModelFailsHonestly() throws Exception {
        if (!new OpenCodeHarnessAdapter(new JGitWorkspaceDiffService(),
                new DStudioPaths(tempDir.resolve("dstudio-home")))
                        .testConnection().success()) {
            return;
        }
        Path workspace = gitWorkspace();
        registry.updateConfiguration("opencode", Map.of(
                "model", "ollama/definitely-not-an-installed-model-xyz"));
        ExecutionTask submitted = executionService.submit(
                new ExecutionService.ExecutionRequest("opencode", null, null,
                        "Create a file named nope.txt", null, null, null, false,
                        workspace.toString(), Duration.ofMinutes(6)));
        ExecutionTask task = awaitTerminal(submitted.getId());
        assertEquals(ExecutionStatus.FAILED, task.getStatus());
        assertTrue(task.getErrorMessage() != null && !task.getErrorMessage().isBlank(),
                "failure must carry a diagnostic message");
    }
}
