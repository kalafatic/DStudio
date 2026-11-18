package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceService;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceService;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceEngineStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceSessionStore;
import eu.kalafatic.dstudio.teip.server.DStudioServer;
import eu.kalafatic.dstudio.teip.server.InferenceHttpHandler;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7 (S4): direct-inference sessions over HTTP — create/list/history, blocking
 * message, SSE streaming, cancellation, error semantics and shared API authentication.
 */
class InferenceSessionApiTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private SqliteDatabaseManager databaseManager;
    private InferenceEngineRegistry registry;
    private InferenceService inferenceService;
    private DStudioServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("session-api.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        registry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        registry.selectDefaultEngine("enr");
        inferenceService = new DefaultInferenceService(registry,
                new SqliteInferenceSessionStore(databaseManager.connection()),
                new DefaultEventBus());
        server = new DStudioServer("127.0.0.1", 0, null, null, registry,
                (exchange) -> exchange.close(), true, null, inferenceService, null);
        server.start();
        port = server.getBoundPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        databaseManager.close();
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String createSession() throws Exception {
        HttpResponse<String> response = send("POST", InferenceHttpHandler.PATH + "/sessions",
                "{\"engine\":\"enr\",\"model\":\"demo:1\",\"systemPrompt\":\"be terse\"}");
        assertEquals(201, response.statusCode());
        return mapper.readTree(response.body()).get("id").asText();
    }

    @Test
    void sessionCreateListAndHistory() throws Exception {
        String sessionId = createSession();
        HttpResponse<String> list = send("GET", InferenceHttpHandler.PATH + "/sessions", null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains(sessionId));

        HttpResponse<String> detail = send("GET", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId, null);
        assertEquals(200, detail.statusCode());
        JsonNode node = mapper.readTree(detail.body());
        assertEquals("enr", node.get("engineId").asText());
        assertEquals("demo:1", node.get("model").asText());
        assertEquals("OPEN", node.get("status").asText());
    }

    @Test
    void blockingMessagePersistsConversation() throws Exception {
        String sessionId = createSession();
        HttpResponse<String> first = send("POST", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId + "/messages", "{\"content\":\"hello\"}");
        assertEquals(200, first.statusCode());
        assertTrue(first.body().contains("fake answer"));
        HttpResponse<String> second = send("POST", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId + "/messages", "{\"content\":\"again\"}");
        assertEquals(200, second.statusCode());

        HttpResponse<String> detail = send("GET", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId, null);
        JsonNode node = mapper.readTree(detail.body());
        assertEquals(4, node.get("messages").size());
        assertEquals("hello", node.get("messages").get(0).get("content").asText());
        assertEquals("assistant", node.get("messages").get(1).get("role").asText());
    }

    @Test
    void streamRouteEmitsSseChunks() throws Exception {
        String sessionId = createSession();
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + InferenceHttpHandler.PATH + "/sessions/"
                        + sessionId + "/messages/stream").toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.getOutputStream().write("{\"content\":\"stream me\"}"
                .getBytes(StandardCharsets.UTF_8));
        connection.getOutputStream().close();
        assertEquals(200, connection.getResponseCode());
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && lines.size() < 20) {
                lines.add(line);
            }
        }
        connection.disconnect();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("event: chunk")),
                "lines: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("event: done")),
                "lines: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("data:")
                        && l.contains("fake answer")),
                "lines: " + lines);
    }

    @Test
    void cancelIsIdempotentAndTerminal() throws Exception {
        String sessionId = createSession();
        HttpResponse<String> cancel = send("POST", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId + "/cancel", null);
        assertEquals(200, cancel.statusCode());
        HttpResponse<String> again = send("POST", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId + "/cancel", null);
        assertEquals(200, again.statusCode());
        HttpResponse<String> detail = send("GET", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId, null);
        assertEquals("CANCELLED", mapper.readTree(detail.body()).get("status").asText());
        // terminal sessions reject new messages
        HttpResponse<String> message = send("POST", InferenceHttpHandler.PATH + "/sessions/"
                + sessionId + "/messages", "{\"content\":\"later\"}");
        assertEquals(409, message.statusCode());
        assertEquals("CONFLICT", mapper.readTree(message.body()).get("code").asText());
    }

    @Test
    void unknownSessionIs404() throws Exception {
        HttpResponse<String> response = send("POST", InferenceHttpHandler.PATH
                + "/sessions/inf-missing/messages", "{\"content\":\"hi\"}");
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", mapper.readTree(response.body()).get("code").asText());
    }

    @Test
    void unknownEngineIs404() throws Exception {
        HttpResponse<String> response = send("POST", InferenceHttpHandler.PATH + "/sessions",
                "{\"engine\":\"missing\",\"model\":\"m\"}");
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", mapper.readTree(response.body()).get("code").asText());
    }

    @Test
    void apiRequiresTokenWhenConfigured() throws Exception {
        System.setProperty("dstudio.teip.auth.token", "session-secret-token");
        try {
            DStudioServer secured = new DStudioServer("127.0.0.1", 0, null, null, registry,
                    (exchange) -> exchange.close(), false, null, inferenceService, null);
            secured.start();
            try {
                int securedPort = secured.getBoundPort();
                HttpRequest noAuth = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + securedPort
                                + InferenceHttpHandler.PATH + "/sessions"))
                        .GET().build();
                HttpResponse<String> denied = HttpClient.newHttpClient().send(noAuth,
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(401, denied.statusCode());
                assertTrue(denied.body().contains("AUTHENTICATION_FAILED"));

                HttpRequest withToken = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + securedPort
                                + InferenceHttpHandler.PATH + "/sessions"))
                        .header("Authorization", "Bearer session-secret-token")
                        .GET().build();
                HttpResponse<String> allowed = HttpClient.newHttpClient().send(withToken,
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, allowed.statusCode());
            } finally {
                secured.stop();
            }
        } finally {
            System.clearProperty("dstudio.teip.auth.token");
        }
    }
}
