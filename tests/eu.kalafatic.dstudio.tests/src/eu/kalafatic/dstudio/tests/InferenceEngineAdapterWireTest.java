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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ENR and Ollama inference-engine adapter wire tests (Phase 6, WP-6.7). Stub ENR /enr/v1
 * server built against the canonical ENR 1.0 wire contract (health at /enr/v1/health,
 * runtime status with running/cpu/heap/devices); the Ollama adapter is verified against
 * a stub /api/tags + /api/chat server.
 */
class InferenceEngineAdapterWireTest {

    private HttpServer enrStub;
    private int enrPort;

    @BeforeEach
    void setUp() throws Exception {
        enrStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        enrStub.createContext("/enr/v1/health", exchange -> respond(exchange, 200,
                "{\"application\":\"ENR\",\"status\":\"UP\",\"ready\":true}"));
        enrStub.createContext("/enr/v1/models", exchange -> respond(exchange, 200,
                "[{\"id\":\"ollama:demo:1\",\"name\":\"demo:1\",\"provider\":\"ollama\","
                        + "\"engine\":\"ollama\",\"sizeBytes\":1024,\"available\":true}]"));
        enrStub.createContext("/enr/v1/chat", exchange -> respond(exchange, 200,
                "{\"content\":\"enr stub answer\",\"finishReason\":\"stop\","
                        + "\"engine\":\"ollama\",\"model\":\"demo:1\",\"durationMs\":9}"));
        enrStub.createContext("/enr/v1/runtime", exchange -> respond(exchange, 200,
                "{\"running\":true,"
                        + "\"cpu\":{\"processors\":4,\"loadAverage\":0.2},"
                        + "\"heap\":{\"usedBytes\":134217728,\"maxBytes\":1073741824},"
                        + "\"devices\":[{\"index\":0,\"type\":\"cpu\",\"name\":\"CPU\","
                        + "\"totalMemoryBytes\":1073741824,\"freeMemoryBytes\":939524096,"
                        + "\"available\":true},{\"index\":0,\"type\":\"cuda\","
                        + "\"name\":\"RTX 5070 Ti\",\"totalMemoryBytes\":16611278848,"
                        + "\"freeMemoryBytes\":12000000000,\"available\":true}],"
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
        assertEquals(0.2, status.systemLoadAverage(), 0.001);
        assertEquals(1024, status.maxHeapMb());
        assertEquals(128, status.usedHeapMb());
        assertTrue(status.gpuDetected(), "cuda device must be reported");
        assertEquals("RTX 5070 Ti", status.gpuDevice());
        assertTrue(status.gpuTotalVramMb() > 0);
        assertTrue(status.activeEngines().contains("ollama"));
    }

    @Test
    void enrLegacyHealthFallbackStillConnects() throws Exception {
        HttpServer legacyStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        legacyStub.createContext("/health", exchange -> respond(exchange, 200,
                "{\"application\":\"ENR\",\"status\":\"UP\"}"));
        legacyStub.start();
        try {
            int port = legacyStub.getAddress().getPort();
            EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
            adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port));
            assertTrue(adapter.testConnection().success(), adapter.testConnection().message());
        } finally {
            legacyStub.stop(0);
        }
    }

    @Test
    void enrStreamingChatWire() throws Exception {
        HttpServer streamStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        streamStub.createContext("/enr/v1/chat/stream", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("data: {\"chunk\":\"hello \",\"engine\":\"enr-native\","
                        .getBytes(StandardCharsets.UTF_8));
                out.write("\"done\":false}\n\n".getBytes(StandardCharsets.UTF_8));
                out.write("data: {\"chunk\":\"world\",\"engine\":\"enr-native\","
                        .getBytes(StandardCharsets.UTF_8));
                out.write("\"done\":false}\n\n".getBytes(StandardCharsets.UTF_8));
                out.write("event: done\ndata: {\"chunk\":\"\",\"engine\":\"enr-native\","
                        .getBytes(StandardCharsets.UTF_8));
                out.write("\"done\":true}\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        });
        streamStub.start();
        try {
            int port = streamStub.getAddress().getPort();
            EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
            adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port));
            StringBuilder content = new StringBuilder();
            AtomicInteger doneCount = new AtomicInteger();
            adapter.streamChat(new InferenceEngineAdapter.InferenceChatRequest("demo:1",
                    List.of(InferenceEngineAdapter.InferenceChatMessage.user("hi")),
                    0.7, 64), chunk -> {
                if (chunk.done()) {
                    doneCount.incrementAndGet();
                } else {
                    content.append(chunk.text());
                }
            }, () -> false);
            assertEquals("hello world", content.toString());
            assertEquals(1, doneCount.get(), "exactly one done chunk expected");
        } finally {
            streamStub.stop(0);
        }
    }

    @Test
    void enrStreamingCancellationAbortsPromptly() throws Exception {
        HttpServer slowStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowStub.createContext("/enr/v1/chat/stream", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int i = 0; i < 600; i++) {
                    out.write("data: {\"chunk\":\"tok\",\"engine\":\"enr-native\","
                            .getBytes(StandardCharsets.UTF_8));
                    out.write("\"done\":false}\n\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException e) {
                // client aborted or stub stopped: expected on cancellation
            }
        });
        slowStub.start();
        try {
            int port = slowStub.getAddress().getPort();
            EnrInferenceEngineAdapter adapter = new EnrInferenceEngineAdapter();
            adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port));
            long start = System.currentTimeMillis();
            AtomicBoolean cancelledAfterFirstChunk = new AtomicBoolean();
            adapter.streamChat(new InferenceEngineAdapter.InferenceChatRequest("demo:1",
                    List.of(InferenceEngineAdapter.InferenceChatMessage.user("count")),
                    0.7, 64), chunk -> cancelledAfterFirstChunk.set(true),
                    cancelledAfterFirstChunk::get);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 15_000,
                    "cancellation must abort the stream promptly (took " + elapsed + " ms)");
        } finally {
            slowStub.stop(0);
        }
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
