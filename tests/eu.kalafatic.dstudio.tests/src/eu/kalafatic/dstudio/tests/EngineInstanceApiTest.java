package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.application.DStudioInferenceEngineFactory;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7 (S3): runtime-instance administration over HTTP — multiple instances of the
 * same provider type can be created, configured and removed through the backend.
 */
class EngineInstanceApiTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private InferenceEngineRegistry registry;
    private DStudioServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("instances.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        registry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        InferenceService inferenceService = new DefaultInferenceService(registry,
                new SqliteInferenceSessionStore(databaseManager.connection()),
                new DefaultEventBus());
        server = new DStudioServer("127.0.0.1", 0, null, null, registry,
                (exchange) -> exchange.close(), true, null, inferenceService,
                new DStudioInferenceEngineFactory());
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
    void createConfigureListAndRemoveInstance() throws Exception {
        HttpResponse<String> created = send("POST", InferenceHttpHandler.PATH + "/engines",
                "{\"type\":\"OLLAMA\",\"id\":\"ollama-remote\",\"name\":\"Remote Ollama\","
                        + "\"config\":{\"endpoint\":\"http://gpu-server:11434\"}}");
        assertEquals(201, created.statusCode());

        HttpResponse<String> list = send("GET", InferenceHttpHandler.PATH + "/engines", null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("ollama-remote"));
        assertTrue(list.body().contains("Remote Ollama"));

        // second instance of the same provider type: distinct id, independent config
        HttpResponse<String> second = send("POST", InferenceHttpHandler.PATH + "/engines",
                "{\"type\":\"OLLAMA\",\"id\":\"ollama-local-2\",\"config\":"
                        + "{\"endpoint\":\"http://127.0.0.1:11434\"}}");
        assertEquals(201, second.statusCode());
        assertEquals(3, registry.listEngines().size());
        assertEquals("http://gpu-server:11434",
                registry.getConfiguration("ollama-remote").get("endpoint"));

        // duplicate id rejected
        HttpResponse<String> duplicate = send("POST", InferenceHttpHandler.PATH + "/engines",
                "{\"type\":\"OLLAMA\",\"id\":\"ollama-remote\"}");
        assertEquals(409, duplicate.statusCode());

        // unknown type rejected
        HttpResponse<String> unknown = send("POST", InferenceHttpHandler.PATH + "/engines",
                "{\"type\":\"NOPE\",\"id\":\"x\"}");
        assertEquals(422, unknown.statusCode());

        // removal persists (descriptor deleted from the store)
        HttpResponse<String> removed = send("DELETE", InferenceHttpHandler.PATH
                + "/engines/ollama-remote", null);
        assertEquals(200, removed.statusCode());
        assertTrue(registry.findEngine("ollama-remote").isEmpty());
        assertEquals(2, registry.listEngines().size());
        HttpResponse<String> again = send("DELETE", InferenceHttpHandler.PATH
                + "/engines/ollama-remote", null);
        assertEquals(404, again.statusCode());
    }

    @Test
    void factoryCreatesOpenAiCompatibleInstances() {
        var adapter = new DStudioInferenceEngineFactory().createEngine("OPENAI_COMPATIBLE",
                "openai-cloud", "Cloud OpenAI", java.util.Map.of("endpoint",
                        "https://api.openai.com/v1", "apiKey", "sk-secret"));
        assertTrue(adapter.isPresent());
        assertEquals("openai-cloud", adapter.get().getEngineId());
        assertEquals("OPENAI_COMPATIBLE", adapter.get().getEngineType());
        // secrets stay masked outside the adapter
        assertFalse(adapter.get().getConnectionConfiguration().get("apiKey")
                .contains("sk-secret"));
        var unknown = new DStudioInferenceEngineFactory().createEngine("MISSING",
                "x", "X", java.util.Map.of());
        assertTrue(unknown.isEmpty());
    }
}
