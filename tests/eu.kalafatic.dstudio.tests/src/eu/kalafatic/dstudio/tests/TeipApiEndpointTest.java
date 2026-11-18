package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.kalafatic.dstudio.core.config.DStudioConfiguration;
import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.service.DefaultExecutionService;
import eu.kalafatic.dstudio.core.service.DefaultReviewService;
import eu.kalafatic.dstudio.infrastructure.config.DStudioPaths;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeipApiEndpointTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private SqliteDatabaseManager databaseManager;
    private DefaultHarnessRegistry registry;
    private DefaultExecutionService executionService;
    private DStudioServer server;
    private FakeHarnessAdapter adapter;
    private int port;
    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("teip-test.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        adapter = new FakeHarnessAdapter("fake", "FAKE");
        registry.registerAdapter(adapter);
        registry.selectDefaultHarness("fake");
        executionService = new DefaultExecutionService(registry,
                new SqliteExecutionStore(databaseManager.connection()),
                new SqliteReviewStore(databaseManager.connection()), null,
                new DefaultEventBus());
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
        port = extractPort();
        workspace = tempDir.resolve("teip-workspace");
        Files.createDirectories(workspace);
    }

    private int extractPort() throws Exception {
        var field = DStudioServer.class.getDeclaredField("server");
        field.setAccessible(true);
        var httpServer = (com.sun.net.httpserver.HttpServer) field.get(server);
        return httpServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        executionService.shutdown();
        databaseManager.close();
    }

    private HttpResponse<String> send(String method, String pathOrUrl, String body,
            Map<String, String> headers) throws Exception {
        String url = pathOrUrl.startsWith("http") ? pathOrUrl
                : "http://127.0.0.1:" + port + pathOrUrl;
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
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

    private String executionBody(String idempotencyKey, String instruction,
            String version) {
        return """
                {
                  "protocol": "TEIP",
                  "version": "%s",
                  "requestId": "req-1",
                  "correlationId": "corr-1",
                  "idempotencyKey": "%s",
                  "timestamp": "2026-11-16T18:00:00Z",
                  "caller": {"system": "TRYST", "projectId": "proj-1", "taskId": "task-1"},
                  "capability": "CODE_IMPLEMENTATION",
                  "mode": "DEFAULT",
                  "model": "ollama/test:latest",
                  "instruction": "%s",
                  "contextReferences": [{"type": "FILE", "path": "src/Main.java"}],
                  "grantedToolScopes": ["read:workspace", "write:workspace"],
                  "parameters": {"workingDirectory": "%s"}
                }
                """.formatted(version, idempotencyKey, instruction, workspace);
    }

    @Test
    void healthReportsUp() throws Exception {
        HttpResponse<String> response = send("GET", "/teip/v1/health", null, Map.of());
        assertEquals(200, response.statusCode());
        JsonNode node = mapper.readTree(response.body());
        assertEquals("TEIP", node.get("protocol").asText());
        assertEquals("1.1", node.get("version").asText());
        assertEquals("UP", node.get("status").asText());
        assertEquals("DSTUDIO", node.get("application").asText());
        assertTrue(node.get("ready").asBoolean());
    }

    @Test
    void capabilitiesListHarnesses() throws Exception {
        HttpResponse<String> response = send("GET", "/teip/v1/capabilities", null, Map.of());
        assertEquals(200, response.statusCode());
        JsonNode node = mapper.readTree(response.body());
        assertTrue(node.get("capabilities").isArray());
        assertEquals("TEIP", node.get("protocol").asText());
        assertTrue(node.get("features").get("cancellation").asBoolean());
    }

    @Test
    void executionLifecycleOverHttp() throws Exception {
        HttpResponse<String> created = send("POST", "/teip/v1/executions",
                executionBody("idem-1", "Refactor UserService.", "1.1"), Map.of());
        assertEquals(202, created.statusCode());
        JsonNode accepted = mapper.readTree(created.body());
        String executionId = accepted.get("executionId").asText();
        assertNotNull(executionId);
        assertEquals("QUEUED", accepted.get("status").asText());
        assertTrue(accepted.get("links").get("status").asText().contains(executionId));
        Thread.sleep(400);
        HttpResponse<String> status = send("GET",
                "/teip/v1/executions/" + executionId, null, Map.of());
        assertEquals(200, status.statusCode());
        JsonNode statusNode = mapper.readTree(status.body());
        assertEquals("COMPLETED", statusNode.get("status").asText());
        HttpResponse<String> result = send("GET",
                "/teip/v1/executions/" + executionId + "/result", null, Map.of());
        assertEquals(200, result.statusCode());
        JsonNode resultNode = mapper.readTree(result.body());
        assertEquals("COMPLETED", resultNode.get("status").asText());
        assertTrue(resultNode.get("modifiedFiles").isArray());
        assertTrue(resultNode.get("summary").asText().contains("fake summary"));
        HttpResponse<String> events = send("GET",
                "/teip/v1/executions/" + executionId + "/events?afterSequence=0&limit=50",
                null, Map.of());
        assertEquals(200, events.statusCode());
        JsonNode eventsNode = mapper.readTree(events.body());
        assertTrue(eventsNode.get("events").isArray());
        assertTrue(eventsNode.get("events").size() >= 3);
        long next = eventsNode.get("nextSequence").asLong();
        assertTrue(next >= 3);
        HttpResponse<String> tail = send("GET",
                "/teip/v1/executions/" + executionId + "/events?afterSequence=" + next,
                null, Map.of());
        JsonNode tailNode = mapper.readTree(tail.body());
        assertTrue(tailNode.get("events").isEmpty());
    }

    @Test
    void idempotentReplayReturns200WithSameExecution() throws Exception {
        String body = executionBody("idem-2", "Do something.", "1.1");
        HttpResponse<String> first = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(202, first.statusCode());
        String firstId = mapper.readTree(first.body()).get("executionId").asText();
        Thread.sleep(400);
        HttpResponse<String> second = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(200, second.statusCode());
        String secondId = mapper.readTree(second.body()).get("executionId").asText();
        assertEquals(firstId, secondId);
        HttpResponse<String> third = send("POST", "/teip/v1/executions", body,
                Map.of("Idempotency-Key", "idem-2"));
        assertEquals(200, third.statusCode());
    }

    @Test
    void resultConflictWhileRunning() throws Exception {
        adapter.setMode(FakeHarnessAdapter.Mode.HANG);
        HttpResponse<String> created = send("POST", "/teip/v1/executions",
                executionBody("idem-3", "Long task.", "1.1"), Map.of());
        String executionId = mapper.readTree(created.body()).get("executionId").asText();
        HttpResponse<String> result = send("GET",
                "/teip/v1/executions/" + executionId + "/result", null, Map.of());
        assertEquals(409, result.statusCode());
        JsonNode error = mapper.readTree(result.body());
        assertEquals("CONFLICT", error.get("code").asText());
        assertTrue(error.get("details").get("currentStatus").asText()
                .equals("RUNNING") || error.get("details").get("currentStatus").asText()
                        .equals("QUEUED"));
        send("POST", "/teip/v1/executions/" + executionId + "/cancel", null, Map.of());
    }

    @Test
    void cancelIsIdempotentAndUnknownIs404() throws Exception {
        adapter.setMode(FakeHarnessAdapter.Mode.HANG);
        HttpResponse<String> created = send("POST", "/teip/v1/executions",
                executionBody("idem-4", "Long task.", "1.1"), Map.of());
        String executionId = mapper.readTree(created.body()).get("executionId").asText();
        HttpResponse<String> cancel1 = send("POST",
                "/teip/v1/executions/" + executionId + "/cancel", null, Map.of());
        assertEquals(200, cancel1.statusCode());
        JsonNode cancelNode = mapper.readTree(cancel1.body());
        assertEquals("CANCELLED", cancelNode.get("status").asText());
        Thread.sleep(300);
        HttpResponse<String> cancel2 = send("POST",
                "/teip/v1/executions/" + executionId + "/cancel", null, Map.of());
        assertEquals(200, cancel2.statusCode());
        HttpResponse<String> unknown = send("POST",
                "/teip/v1/executions/task-missing/cancel", null, Map.of());
        assertEquals(404, unknown.statusCode());
        assertEquals("NOT_FOUND", mapper.readTree(unknown.body()).get("code").asText());
    }

    @Test
    void versionMismatchRejected() throws Exception {
        HttpResponse<String> response = send("POST", "/teip/v1/executions",
                executionBody("idem-5", "Do it.", "2.0"), Map.of());
        assertEquals(400, response.statusCode());
        assertEquals("VERSION_MISMATCH", mapper.readTree(response.body()).get("code").asText());
    }

    @Test
    void missingInstructionRejected() throws Exception {
        String body = """
                {"protocol": "TEIP", "version": "1.1", "idempotencyKey": "idem-6",
                 "parameters": {"workingDirectory": "%s"}}
                """.formatted(workspace);
        HttpResponse<String> response = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(400, response.statusCode());
        JsonNode error = mapper.readTree(response.body());
        assertEquals("VALIDATION_FAILED", error.get("code").asText());
        assertEquals("instruction", error.get("details").get("field").asText());
    }

    @Test
    void unknownHarnessRejectedWith422() throws Exception {
        String body = """
                {"protocol": "TEIP", "version": "1.1", "instruction": "Do it",
                 "parameters": {"workingDirectory": "%s", "harness": "missing"}}
                """.formatted(workspace);
        HttpResponse<String> response = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(422, response.statusCode());
        assertEquals("CAPABILITY_UNSUPPORTED",
                mapper.readTree(response.body()).get("code").asText());
    }

    @Test
    void missingWorkingDirectoryRejected() throws Exception {
        String body = """
                {"protocol": "TEIP", "version": "1.1", "instruction": "Do it"}
                """;
        HttpResponse<String> response = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(400, response.statusCode());
        assertEquals("VALIDATION_FAILED", mapper.readTree(response.body()).get("code").asText());
    }

    @Test
    void wrongMethodRejected() throws Exception {
        HttpResponse<String> response = send("DELETE", "/teip/v1/health", null, Map.of());
        assertEquals(405, response.statusCode());
        assertEquals("METHOD_NOT_ALLOWED", mapper.readTree(response.body()).get("code").asText());
    }

    @Test
    void unknownPathIs404() throws Exception {
        HttpResponse<String> response = send("GET", "/teip/v1/nope", null, Map.of());
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", mapper.readTree(response.body()).get("code").asText());
    }

    private HttpResponse<String> sendOn(String method, int targetPort, String path,
            String body, Map<String, String> headers) throws Exception {
        return send(method, "http://127.0.0.1:" + targetPort + path, body, headers);
    }

    @Test
    void bearerTokenEnforcedWhenConfigured() throws Exception {
        System.setProperty("dstudio.teip.auth.token", "secret-token-42");
        try {
            DStudioConfiguration configuration = new DStudioConfiguration(
                    DStudioConfiguration.APP_NAME, DStudioConfiguration.APP_VERSION,
                    tempDir.resolve("cfg2"), "127.0.0.1", 0,
                    DStudioConfiguration.NETWORK_LOOPBACK, 0, false, "", "",
                    DStudioConfiguration.DEFAULT_EXECUTION_TIMEOUT_SECONDS, "", "");
            TeipExecutionManager manager = new TeipExecutionManager(registry,
                    executionService, new DefaultReviewService(
                            new SqliteReviewStore(databaseManager.connection())),
                    configuration);
            DStudioServer tokenServer = new DStudioServer("127.0.0.1", 0, registry,
                    executionService, null, new TeipHttpHandler(manager, false), false,
                    null, null, null);
            tokenServer.start();
            try {
                var field = DStudioServer.class.getDeclaredField("server");
                field.setAccessible(true);
                var httpServer = (com.sun.net.httpserver.HttpServer) field.get(tokenServer);
                int tokenPort = httpServer.getAddress().getPort();
                HttpResponse<String> noAuth = sendOn("GET", tokenPort, "/teip/v1/health",
                        null, Map.of());
                assertEquals(401, noAuth.statusCode());
                assertEquals("AUTHENTICATION_FAILED",
                        mapper.readTree(noAuth.body()).get("code").asText());
                HttpResponse<String> wrong = sendOn("GET", tokenPort, "/teip/v1/health",
                        null, Map.of("Authorization", "Bearer wrong"));
                assertEquals(401, wrong.statusCode());
                HttpResponse<String> right = sendOn("GET", tokenPort, "/teip/v1/health",
                        null, Map.of("Authorization", "Bearer secret-token-42"));
                assertEquals(200, right.statusCode());
                assertEquals("UP", mapper.readTree(right.body()).get("status").asText());
            } finally {
                tokenServer.stop();
            }
        } finally {
            System.clearProperty("dstudio.teip.auth.token");
        }
    }

    @Test
    void deprecatedAliasesAccepted() throws Exception {
        String body = """
                {"protocol": "TEIP", "version": "1.1", "idempotencyKey": "idem-7",
                 "aiMode": "DEFAULT", "instruction": "Alias task",
                 "caller": {"app": "EVO"},
                 "mcpToolScopes": ["read:workspace"],
                 "parameters": {"workingDirectory": "%s"}}
                """.formatted(workspace);
        HttpResponse<String> response = send("POST", "/teip/v1/executions", body, Map.of());
        assertEquals(202, response.statusCode());
    }
}
