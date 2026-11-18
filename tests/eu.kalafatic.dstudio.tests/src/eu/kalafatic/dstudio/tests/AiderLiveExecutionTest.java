package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionStatus;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.service.DefaultExecutionService;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.harness.aider.AiderHarnessAdapter;
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

class AiderLiveExecutionTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private DefaultHarnessRegistry registry;
    private ExecutionService executionService;

    @BeforeEach
    void setUp() {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("aider-live.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                new SecretCodec(tempDir)));
        registry.registerAdapter(new AiderHarnessAdapter(
                new JGitWorkspaceDiffService()));
        registry.updateConfiguration("aider", Map.of(
                "model", "ollama/qwen3-coder-30b-32k:latest"));
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
        Path repo = tempDir.resolve("aider-workspace");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "# Aider workspace\n");
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
        throw new AssertionError("Real Aider execution did not terminate in time");
    }

    @Test
    @Timeout(420)
    void realAiderExecutionCreatesMarkerAndFiltersArtifacts() throws Exception {
        if (!new AiderHarnessAdapter(new JGitWorkspaceDiffService())
                .testConnection().success()) {
            return;
        }
        Path workspace = gitWorkspace();
        ExecutionTask submitted = executionService.submit(
                new ExecutionService.ExecutionRequest("aider", null, null,
                        "Create a new file named aider-live-marker.txt containing "
                                + "exactly the text: dstudio-aider-live-ok", null,
                        null, null, false, workspace.toString(),
                        Duration.ofMinutes(6)));
        ExecutionTask task = awaitTerminal(submitted.getId());
        assertEquals(ExecutionStatus.COMPLETED, task.getStatus(),
                "task error: " + task.getErrorMessage());
        assertTrue(Files.isRegularFile(workspace.resolve("aider-live-marker.txt")),
                "marker file not created in workspace");
        assertEquals("dstudio-aider-live-ok",
                Files.readString(workspace.resolve("aider-live-marker.txt")).trim());
        List<ExecutionEvent> events = executionService.getEvents(submitted.getId(), 0, 500);
        assertTrue(events.stream().anyMatch(e ->
                e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_LOG));
        assertFalse(events.stream().anyMatch(e ->
                e.message() != null && e.message().contains(".aider.chat.history")));
    }
}
