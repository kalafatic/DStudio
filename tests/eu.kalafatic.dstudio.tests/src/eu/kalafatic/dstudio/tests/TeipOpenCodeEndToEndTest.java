package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.kalafatic.dstudio.core.config.DStudioConfiguration;
import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.service.DefaultExecutionService;
import eu.kalafatic.dstudio.core.service.DefaultReviewService;
import eu.kalafatic.dstudio.harness.opencode.OpenCodeHarnessAdapter;
import eu.kalafatic.dstudio.infrastructure.config.DStudioPaths;
import eu.kalafatic.dstudio.infrastructure.git.JGitWorkspaceDiffService;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteExecutionStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteReviewStore;
import eu.kalafatic.dstudio.teip.server.DStudioServer;
import eu.kalafatic.dstudio.teip.server.TeipExecutionManager;
import eu.kalafatic.dstudio.teip.server.TeipHttpHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class TeipOpenCodeEndToEndTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private SqliteDatabaseManager databaseManager;
    private DefaultHarnessRegistry registry;
    private DefaultExecutionService executionService;
    private DStudioServer server;
    private int port;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("teip-e2e.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("dstudio-home"));
        registry = new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        registry.registerAdapter(new OpenCodeHarnessAdapter(
                new JGitWorkspaceDiffService(), paths));
        registry.selectDefaultHarness("opencode");
        executionService = new DefaultExecutionService(registry,
                new SqliteExecutionStore(databaseManager.connection()),
                new SqliteReviewStore(databaseManager.connection()),
                new JGitWorkspaceDiffService(), new DefaultEventBus());
        DefaultReviewService reviewService = new DefaultReviewService(
                new SqliteReviewStore(databaseManager.connection()));
        DStudioConfiguration configuration = new DStudioConfiguration(
                DStudioConfiguration.APP_NAME, DStudioConfiguration.APP_VERSION,
                tempDir.resolve("cfg"), "127.0.0.1", 0,
                DStudioConfiguration.NETWORK_LOOPBACK, 0, true, "", "",
                DStudioConfiguration.DEFAULT_EXECUTION_TIMEOUT_SECONDS, "", "");
        TeipExecutionManager manager = new TeipExecutionManager(registry,
                executionService, reviewService, configuration);
        server = new DStudioServer("127.0.0.1", 0, registry, executionService, null,
                new TeipHttpHandler(manager, true), true, null, null, null);
        server.start();
        var field = DStudioServer.class.getDeclaredField("server");
        field.setAccessible(true);
        var httpServer = (com.sun.net.httpserver.HttpServer) field.get(server);
        port = httpServer.getAddress().getPort();
        workspace = tempDir.resolve("teip-workspace");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("README.md"), "# TEIP workspace\n");
        try (Git git = Git.init().setDirectory(workspace.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();
        }
    }

    @AfterEach
    void tearDown() {
        server.stop();
        executionService.shutdown();
        databaseManager.close();
    }

    private HttpResponse<String> send(String method, String path, String body,
            Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HttpClient.newHttpClient().send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @Timeout(420)
    void externalClientDrivesRealOpenCodeThroughTeip() throws Exception {
        if (!new OpenCodeHarnessAdapter(new JGitWorkspaceDiffService(),
                new DStudioPaths(tempDir.resolve("dstudio-home")))
                        .testConnection().success()) {
            return;
        }
        String body = """
                {
                  "protocol": "TEIP",
                  "version": "1.1",
                  "requestId": "req-e2e-1",
                  "correlationId": "corr-e2e-1",
                  "idempotencyKey": "idem-e2e-opencode-1",
                  "timestamp": "2026-11-16T18:00:00Z",
                  "caller": {"system": "TRYST", "projectId": "proj-e2e", "taskId": "task-e2e"},
                  "capability": "CODE_IMPLEMENTATION",
                  "mode": "DEFAULT",
                  "instruction": "Create a new file named teip-marker.txt containing exactly the text: dstudio-teip-e2e-ok",
                  "grantedToolScopes": ["read:workspace", "write:workspace"],
                  "parameters": {"workingDirectory": "%s", "harness": "opencode"}
                }
                """.formatted(workspace);
        HttpResponse<String> created = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(202, created.statusCode(), created.body());
        String executionId = mapper.readTree(created.body()).get("executionId").asText();
        for (int i = 0; i < 600; i++) {
            HttpResponse<String> status = send("GET",
                    "/teip/v1/executions/" + executionId, null, Map.of());
            String current = mapper.readTree(status.body()).get("status").asText();
            if (current.equals("COMPLETED") || current.equals("FAILED")
                    || current.equals("CANCELLED")) {
                assertEquals("COMPLETED", current,
                        "result: " + send("GET", "/teip/v1/executions/"
                                + executionId + "/result", null, Map.of()).body());
                break;
            }
            Thread.sleep(1000);
        }
        HttpResponse<String> result = send("GET",
                "/teip/v1/executions/" + executionId + "/result", null, Map.of());
        assertEquals(200, result.statusCode());
        JsonNode resultNode = mapper.readTree(result.body());
        assertEquals("COMPLETED", resultNode.get("status").asText());
        boolean markerListed = false;
        for (JsonNode file : resultNode.get("modifiedFiles")) {
            if (file.asText().equals("teip-marker.txt")) {
                markerListed = true;
            }
        }
        assertTrue(markerListed, "teip-marker.txt missing from modifiedFiles");
        assertTrue(Files.isRegularFile(workspace.resolve("teip-marker.txt")));
        assertEquals("dstudio-teip-e2e-ok",
                Files.readString(workspace.resolve("teip-marker.txt")).trim());
        HttpResponse<String> events = send("GET",
                "/teip/v1/executions/" + executionId + "/events?afterSequence=0&limit=500",
                null, Map.of());
        JsonNode eventsNode = mapper.readTree(events.body());
        assertTrue(eventsNode.get("events").size() >= 3,
                "expected at least 3 streamed events");
    }
}
