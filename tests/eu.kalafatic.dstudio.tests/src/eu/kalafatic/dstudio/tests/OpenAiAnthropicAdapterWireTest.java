package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter.InferenceChatChunk;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter.InferenceChatMessage;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter.InferenceChatRequest;
import eu.kalafatic.dstudio.harness.anthropic.AnthropicInferenceEngineAdapter;
import eu.kalafatic.dstudio.harness.openai.OpenAiCompatibleInferenceEngineAdapter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 7 (S3): wire-level tests of the OpenAI-compatible and Anthropic adapters
 * against in-process stub endpoints (live evidence for the OpenAI-compatible adapter
 * exists separately via Ollama's /v1 in OpenAiCompatibleLiveTest).
 */
class OpenAiAnthropicAdapterWireTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer stub;
    private int port;
    private final AtomicBoolean sawAuthHeader = new AtomicBoolean();

    @BeforeEach
    void setUp() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        stub.start();
        port = stub.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    // ---------------------------------------------------------------- OpenAI

    @Test
    void openAiModelListingAndBlockingChat() throws Exception {
        stub.createContext("/v1/models", exchange -> {
            respond(exchange, 200, """
                    {"object":"list","data":[{"id":"gpt-test","object":"model"}]}""");
        });
        stub.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("gpt-test"));
            assertTrue(body.contains("\"stream\":false"));
            respond(exchange, 200, """
                    {"id":"cmpl-1","model":"gpt-test",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"hello"},
                     "finish_reason":"stop"}]}""");
        });
        OpenAiCompatibleInferenceEngineAdapter adapter =
                new OpenAiCompatibleInferenceEngineAdapter("openai-test", "Test OpenAI");
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port + "/v1",
                "apiKey", "sk-test-key"));
        assertTrue(adapter.testConnection().success());
        List<InferenceEngineAdapter.InferenceModel> models = adapter.listModels();
        assertEquals(1, models.size());
        assertEquals("gpt-test", models.get(0).id());
        var response = adapter.chat(new InferenceChatRequest("gpt-test",
                List.of(InferenceChatMessage.user("hi")), 0.7, 100));
        assertEquals("hello", response.content());
        assertEquals("stop", response.finishReason());
        assertEquals("gpt-test", response.model());
        // apiKey must never leak through the configuration getter
        assertEquals("****** (configured)", adapter.getConnectionConfiguration().get("apiKey"));
    }

    @Test
    void openAiStreamingWithCancellationAndDone() throws Exception {
        stub.createContext("/v1/models", exchange ->
                respond(exchange, 200, "{\"object\":\"list\",\"data\":[]}"));
        stub.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("\"stream\":true"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                writeSse(out, "{\"choices\":[{\"delta\":{\"content\":\"hel\"},\"finish_reason\":null}]}");
                writeSse(out, "{\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":null}]}");
                writeSse(out, "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
                out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        });
        OpenAiCompatibleInferenceEngineAdapter adapter =
                new OpenAiCompatibleInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port + "/v1"));
        List<String> chunks = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean();
        adapter.streamChat(new InferenceChatRequest("gpt-test",
                List.of(InferenceChatMessage.user("hi")), 0.7, 100), chunk -> {
            if (!chunk.text().isEmpty()) {
                chunks.add(chunk.text());
            }
            if (chunk.done()) {
                done.set(true);
            }
        }, () -> false);
        assertEquals(2, chunks.size());
        assertEquals("hel", chunks.get(0));
        assertEquals("lo", chunks.get(1));
        assertTrue(done.get());
    }

    @Test
    void openAiCancellationAbortsStream() throws Exception {
        stub.createContext("/v1/models", exchange ->
                respond(exchange, 200, "{\"object\":\"list\",\"data\":[]}"));
        stub.createContext("/v1/chat/completions", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (int line = 0; line < 1000; line++) {
                    writeSse(out, "{\"choices\":[{\"delta\":{\"content\":\"x\"},"
                            + "\"finish_reason\":null}]}");
                    out.flush();
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (IOException ignored) {
                // client aborted the exchange: expected on cancellation
            }
        });
        OpenAiCompatibleInferenceEngineAdapter adapter =
                new OpenAiCompatibleInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port + "/v1"));
        AtomicBoolean cancelled = new AtomicBoolean();
        List<String> chunks = new ArrayList<>();
        Thread canceller = new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            cancelled.set(true);
        });
        canceller.start();
        long start = System.currentTimeMillis();
        try {
            adapter.streamChat(new InferenceChatRequest("gpt-test",
                    List.of(InferenceChatMessage.user("hi")), 0.7, 100), chunk ->
                    chunks.add(chunk.text()), cancelled::get);
        } catch (Exception expectedOnAbort) {
            // closing the body stream aborts the exchange; the service layer treats
            // this as CANCELLED (verified in InferenceSessionServiceTest)
        }
        long elapsed = System.currentTimeMillis() - start;
        canceller.join(1000);
        assertTrue(elapsed < 10_000, "cancellation must abort the stream promptly ("
                + elapsed + " ms)");
        assertTrue(chunks.size() < 1000, "stream must stop well before the full 1000 lines");
    }

    // ---------------------------------------------------------------- Anthropic

    @Test
    void anthropicModelListingChatAndSystemHandling() throws Exception {
        AtomicBoolean sawKey = new AtomicBoolean();
        stub.createContext("/v1/models", exchange -> {
            sawKey.set(!"".equals(exchange.getRequestHeaders()
                    .getFirst("x-api-key")));
            respond(exchange, 200, "{\"data\":[{\"id\":\"claude-test\"}]}");
        });
        stub.createContext("/v1/messages", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("\"stream\":false"));
            assertTrue(body.contains("\"system\":\"be terse\""));
            assertFalse(body.contains("\"role\":\"system\""));
            respond(exchange, 200, """
                    {"id":"msg-1","model":"claude-test","stop_reason":"end_turn",
                     "content":[{"type":"text","text":"ok"}]}""");
        });
        AnthropicInferenceEngineAdapter adapter =
                new AnthropicInferenceEngineAdapter("anthropic-test", "Test Anthropic");
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port,
                "apiKey", "sk-ant-test"));
        assertTrue(adapter.testConnection().success());
        assertTrue(sawKey.get(), "x-api-key header must be sent");
        assertEquals(1, adapter.listModels().size());
        var response = adapter.chat(new InferenceChatRequest("claude-test",
                List.of(InferenceChatMessage.system("be terse"),
                        InferenceChatMessage.user("hi")), 0.7, 100));
        assertEquals("ok", response.content());
        assertEquals("end_turn", response.finishReason());
        assertEquals("****** (configured)", adapter.getConnectionConfiguration().get("apiKey"));
    }

    @Test
    void anthropicStreamingDeltasAndStop() throws Exception {
        stub.createContext("/v1/models", exchange ->
                respond(exchange, 200, "{\"data\":[]}"));
        stub.createContext("/v1/messages", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("\"stream\":true"));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                writeSse(out, "{\"type\":\"message_start\",\"message\":{\"id\":\"m\"}}");
                writeSse(out, "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"He\"}}");
                writeSse(out, "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"y\"}}");
                writeSse(out, "{\"type\":\"message_stop\"}");
                out.flush();
            }
        });
        AnthropicInferenceEngineAdapter adapter = new AnthropicInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port,
                "apiKey", "k"));
        List<String> chunks = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean();
        adapter.streamChat(new InferenceChatRequest("claude-test",
                List.of(InferenceChatMessage.user("hi")), 0.7, 100), chunk -> {
            if (!chunk.text().isEmpty()) {
                chunks.add(chunk.text());
            }
            if (chunk.done()) {
                done.set(true);
            }
        }, () -> false);
        assertEquals(List.of("He", "y"), chunks);
        assertTrue(done.get());
    }

    @Test
    void anthropicMissingKeyReportsHonestly() throws Exception {
        stub.createContext("/v1/models", exchange ->
                respond(exchange, 401, "{\"error\":{\"message\":\"authentication_error\"}}"));
        AnthropicInferenceEngineAdapter adapter = new AnthropicInferenceEngineAdapter();
        adapter.updateConfiguration(Map.of("endpoint", "http://127.0.0.1:" + port));
        var result = adapter.testConnection();
        assertFalse(result.success());
        assertTrue(result.message().contains("401"), result.message());
    }

    private void writeSse(OutputStream out, String json) throws IOException {
        out.write(("data: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
