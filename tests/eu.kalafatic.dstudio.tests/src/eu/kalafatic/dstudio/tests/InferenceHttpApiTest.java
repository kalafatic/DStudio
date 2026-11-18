package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

/**
 * Headless inference-management API tests (Phase 6, WP-6.7 section 7.4): engines, models,
 * lifecycle, runtime status and chat must be reachable over HTTP without the IDE.
 */
class InferenceHttpApiTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private InferenceEngineRegistry registry;
    private InferenceService inferenceService;
    private DStudioServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("inference-api.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        registry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        registry.registerEngine(new FakeInferenceEngineAdapter("ollama", "OLLAMA"));
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

    @Test
    void enginesListedWithDefault() throws Exception {
        HttpResponse<String> response = send("GET", InferenceHttpHandler.PATH
                + "/engines", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":\"enr\""));
        assertTrue(response.body().contains("\"id\":\"ollama\""));
        assertTrue(response.body().contains("\"default\":true"));
    }

    @Test
    void modelDiscovery() throws Exception {
        HttpResponse<String> response = send("GET", InferenceHttpHandler.PATH
                + "/models?engine=enr", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("enr:demo:1"));
    }

    @Test
    void modelLoadUnload() throws Exception {
        HttpResponse<String> loaded = send("POST", InferenceHttpHandler.PATH
                + "/engines/enr/load", "{\"model\":\"demo:1\"}");
        assertEquals(200, loaded.statusCode());
        assertTrue(loaded.body().contains("\"success\":true"));
        HttpResponse<String> unloaded = send("POST", InferenceHttpHandler.PATH
                + "/engines/enr/unload", "{\"model\":\"demo:1\"}");
        assertEquals(200, unloaded.statusCode());
    }

    @Test
    void runtimeStatus() throws Exception {
        HttpResponse<String> response = send("GET", InferenceHttpHandler.PATH
                + "/engines/enr/runtime", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ready\":true"));
    }

    @Test
    void chatThroughHeadlessApi() throws Exception {
        HttpResponse<String> response = send("POST", InferenceHttpHandler.PATH + "/chat",
                "{\"engine\":\"enr\",\"model\":\"demo:1\",\"messages\":[{\"role\":"
                        + "\"user\",\"content\":\"hi\"}]}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("fake answer"));
    }

    @Test
    void unknownEngineIsNotFound() throws Exception {
        HttpResponse<String> response = send("GET", InferenceHttpHandler.PATH
                + "/models?engine=missing", null);
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("NOT_FOUND"));
    }

    @Test
    void unknownRouteIsNotFound() throws Exception {
        HttpResponse<String> response = send("GET", InferenceHttpHandler.PATH
                + "/unknown", null);
        assertEquals(404, response.statusCode());
    }
}
