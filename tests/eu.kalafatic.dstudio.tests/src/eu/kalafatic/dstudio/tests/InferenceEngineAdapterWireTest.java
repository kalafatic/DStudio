package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.harness.enr.EnrInferenceEngineAdapter;
import eu.kalafatic.dstudio.harness.ollama.OllamaInferenceEngineAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ENR and Ollama inference-engine adapter wire tests (Phase 6, WP-6.7). Stub ENR /enr/v1
 * server; the Ollama adapter is verified against a stub /api/tags + /api/chat server.
 */
class InferenceEngineAdapterWireTest {

    private HttpServer enrStub;
    private int enrPort;

    @BeforeEach
    void setUp() throws Exception {
        enrStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        enrStub.createContext("/health", exchange -> respond(exchange, 200,
                "{\"application\":\"ENR\",\"status\":\"UP\"}"));
        enrStub.createContext("/enr/v1/models", exchange -> respond(exchange, 200,
                "[{\"id\":\"ollama:demo:1\",\"name\":\"demo:1\",\"provider\":\"ollama\","
                        + "\"engine\":\"ollama\",\"sizeBytes\":1024,\"available\":true}]"));
        enrStub.createContext("/enr/v1/chat", exchange -> respond(exchange, 200,
                "{\"content\":\"enr stub answer\",\"finishReason\":\"stop\","
                        + "\"engine\":\"ollama\",\"model\":\"demo:1\",\"durationMs\":9}"));
        enrStub.createContext("/enr/v1/runtime", exchange -> respond(exchange, 200,
                "{\"ready\":true,\"availableProcessors\":4,\"systemLoadAverage\":0.2,"
                        + "\"maxHeapMb\":1024,\"usedHeapMb\":128,"
                        + "\"gpu\":{\"detected\":false,\"deviceName\":\"\","
                        + "\"totalVramMb\":0,\"usedVramMb\":0,\"freeVramMb\":0},"
                        + "\"activeEngines\":[\"ollama\"]}"));
        enrStub.createContext("/enr/v1/models/ollama:demo:1/load", exchange -> respond(
                exchange, 200, "{\"model\":\"ollama:demo:1\",\"success\":true}"));
        enrStub.createContext("/enr/v1/models/ollama:demo:1/unload", exchange -> respond(
                exchange, 200, "{\"model\":\"ollama:demo:1\",\"success\":true}"));
        enrStub.start();
        enrPort = enrStub.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        enrStub.stop(0);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status,
            String body) throws java.io.IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    @Test
    void enrIdentityAndConnection() {
        EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + enrPort));
        assertEquals("enr", adapter.getEngineId());
        assertEquals("ENR", adapter.getEngineType());
        assertTrue(adapter.testConnection().success(), adapter.testConnection().message());
        assertTrue(adapter.isAvailable());
    }

    @Test
    void enrModelDiscovery() {
        EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + enrPort));
        List<InferenceEngineAdapter.InferenceModel> models = adapter.listModels();
        assertEquals(1, models.size());
        assertEquals("ollama:demo:1", models.get(0).id());
    }

    @Test
    void enrChat() {
        EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + enrPort));
        InferenceEngineAdapter.InferenceChatResponse response = adapter.chat(
                new InferenceEngineAdapter.InferenceChatRequest("ollama:demo:1",
                        List.of(InferenceEngineAdapter.InferenceChatMessage.user("hi")),
                        0.7, 128));
        assertEquals("enr stub answer", response.content());
        assertEquals("stop", response.finishReason());
    }

    @Test
    void enrModelLifecycleAndRuntime() {
        EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + enrPort));
        assertTrue(adapter.loadModel("ollama:demo:1"));
        assertTrue(adapter.unloadModel("ollama:demo:1"));
        InferenceEngineAdapter.InferenceRuntimeStatus status = adapter.runtimeStatus();
        assertTrue(status.ready());
        assertEquals(4, status.availableProcessors());
        assertTrue(status.activeEngines().contains("ollama"));
    }

    @Test
    void enrUnreachableReportsUnavailable() {
        EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:1",
                "timeoutSeconds", "5"));
        assertFalse(adapter.testConnection().success());
    }

    @Test
    void ollamaIdentityAndDefaults() {
        OllamaInferenceEngineAdapter adapter = new OllamaInferenceEngineAdapter();
        assertEquals("ollama", adapter.getEngineId());
        assertEquals("OLLAMA", adapter.getEngineType());
        assertEquals("http://localhost:11434",
                adapter.getConnectionConfiguration().get("endpoint"));
    }

    @Test
    void ollamaUnreachableReportsUnavailable() {
        OllamaInferenceEngineAdapter adapter = new OllamaInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:1",
                "timeoutSeconds", "5"));
        assertFalse(adapter.testConnection().success());
    }

    @Test
    void ollamaStubChat() throws Exception {
        HttpServer ollamaStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ollamaStub.createContext("/api/tags", exchange -> respond(exchange, 200,
                "{\"models\":[{\"name\":\"demo:1\",\"size\":1024}]}"));
        ollamaStub.createContext("/api/chat", exchange -> respond(exchange, 200,
                "{\"message\":{\"content\":\"ollama stub answer\"},"
                        + "\"done_reason\":\"stop\"}"));
        ollamaStub.start();
        try {
            int port = ollamaStub.getAddress().getPort();
            OllamaInferenceEngineAdapter adapter = new OllamaInferenceEngineAdapter();
            adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port));
            assertTrue(adapter.testConnection().success());
            assertEquals(1, adapter.listModels().size());
            InferenceEngineAdapter.InferenceChatResponse response = adapter.chat(
                    new InferenceEngineAdapter.InferenceChatRequest("demo:1",
                            List.of(InferenceEngineAdapter.InferenceChatMessage.user("hi")),
                            0.7, 64));
            assertEquals("ollama stub answer", response.content());
        } finally {
            ollamaStub.stop(0);
        }
    }
}
