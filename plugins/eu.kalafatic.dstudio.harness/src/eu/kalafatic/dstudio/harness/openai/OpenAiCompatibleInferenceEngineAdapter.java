package eu.kalafatic.dstudio.harness.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * OpenAI-compatible inference-engine adapter (Phase 7, S3). Talks the OpenAI chat wire
 * contract ({@code /models}, {@code /chat/completions} incl. streaming) against any
 * compatible endpoint — OpenAI itself, local servers (Ollama {@code /v1}, vLLM,
 * LM Studio) or other cloud providers. Multiple instances of this adapter (distinct
 * engine ids) can be registered for different endpoints.
 *
 * <p>Live-verified against a real Ollama {@code /v1} endpoint on this host
 * (model listing, blocking chat and streaming chat).</p>
 */
public class OpenAiCompatibleInferenceEngineAdapter implements InferenceEngineAdapter {

    public static final String DEFAULT_ENGINE_ID = "openai";
    public static final String DEFAULT_ENGINE_NAME = "OpenAI Compatible";
    public static final String ENGINE_TYPE = "OPENAI_COMPATIBLE";

    public static final String CONFIG_ENDPOINT = "endpoint";
    public static final String CONFIG_API_KEY = "apiKey";
    public static final String CONFIG_DEFAULT_MODEL = "defaultModel";
    public static final String CONFIG_TIMEOUT_SECONDS = "timeoutSeconds";

    /** Local Ollama exposes an OpenAI-compatible API here; fully configurable. */
    public static final String DEFAULT_ENDPOINT = "http://localhost:11434/v1";

    private final String engineId;
    private final String engineName;
    private final Map<String, String> config = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiCompatibleInferenceEngineAdapter() {
        this(DEFAULT_ENGINE_ID, DEFAULT_ENGINE_NAME);
    }

    public OpenAiCompatibleInferenceEngineAdapter(String engineId, String engineName) {
        this.engineId = engineId;
        this.engineName = engineName;
        config.put(CONFIG_ENDPOINT, DEFAULT_ENDPOINT);
        config.put(CONFIG_API_KEY, "");
        config.put(CONFIG_DEFAULT_MODEL, "");
        config.put(CONFIG_TIMEOUT_SECONDS, "120");
    }

    @Override
    public String getEngineId() {
        return engineId;
    }

    @Override
    public String getEngineName() {
        return engineName;
    }

    @Override
    public String getEngineType() {
        return ENGINE_TYPE;
    }

    private String endpoint() {
        String base = config.getOrDefault(CONFIG_ENDPOINT, DEFAULT_ENDPOINT).trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private Duration timeout() {
        try {
            return Duration.ofSeconds(Long.parseLong(
                    config.getOrDefault(CONFIG_TIMEOUT_SECONDS, "120")));
        } catch (NumberFormatException e) {
            return Duration.ofSeconds(120);
        }
    }

    @Override
    public boolean isAvailable() {
        return testConnection().success();
    }

    @Override
    public InferenceConnectionTestResult testConnection() {
        try {
            HttpResponse<String> response = send("GET", endpoint() + "/models", null,
                    timeout());
            if (response.statusCode() == 200) {
                return InferenceConnectionTestResult.ok("OpenAI-compatible endpoint "
                        + "available at " + endpoint());
            }
            return InferenceConnectionTestResult.error("Endpoint " + endpoint()
                    + " returned HTTP " + response.statusCode()
                    + (response.statusCode() == 401 ? " (check apiKey)" : ""));
        } catch (Exception e) {
            return InferenceConnectionTestResult.error("Endpoint unreachable at "
                    + endpoint() + ": " + e.getMessage());
        }
    }

    @Override
    public void updateConfiguration(Map<String, String> configuration) {
        if (configuration != null) {
            config.putAll(configuration);
        }
    }

    @Override
    public Map<String, String> getConnectionConfiguration() {
        Map<String, String> result = new LinkedHashMap<>(config);
        if (result.containsKey(CONFIG_API_KEY)
                && !result.get(CONFIG_API_KEY).isBlank()) {
            result.put(CONFIG_API_KEY, "****** (configured)");
        }
        return result;
    }

    @Override
    public List<InferenceModel> listModels() {
        try {
            HttpResponse<String> response = send("GET", endpoint() + "/models", null,
                    timeout());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Model discovery failed: HTTP "
                        + response.statusCode());
            }
            List<InferenceModel> models = new ArrayList<>();
            for (JsonNode node : mapper.readTree(response.body()).path("data")) {
                String id = node.path("id").asText("");
                if (id.isBlank()) {
                    continue;
                }
                models.add(new InferenceModel(id, id, engineId, engineId,
                        0, true));
            }
            return models;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Model discovery failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InferenceRuntimeStatus runtimeStatus() {
        // The OpenAI wire contract has no runtime/health endpoint: report connectivity
        // honestly and leave hardware metrics unknown (zeros).
        return new InferenceRuntimeStatus(testConnection().success(), 0, 0, 0, 0,
                false, "", 0, 0, 0, List.of(engineId));
    }

    @Override
    public boolean loadModel(String modelId) {
        return false; // not supported by the OpenAI wire contract
    }

    @Override
    public boolean unloadModel(String modelId) {
        return false; // not supported by the OpenAI wire contract
    }

    @Override
    public InferenceChatResponse chat(InferenceChatRequest request) {
        try {
            String body = chatBody(request, false);
            long start = System.currentTimeMillis();
            HttpResponse<String> response = send("POST",
                    endpoint() + "/chat/completions", body, timeout());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Chat failed: HTTP " + response.statusCode()
                        + " " + errorSnippet(response.body()));
            }
            JsonNode node = mapper.readTree(response.body());
            String content = node.path("choices").path(0).path("message")
                    .path("content").asText("");
            String finishReason = node.path("choices").path(0)
                    .path("finish_reason").asText("stop");
            String model = node.path("model").asText(request.model());
            return new InferenceChatResponse(content, finishReason, engineId, model,
                    System.currentTimeMillis() - start);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void streamChat(InferenceChatRequest request, Consumer<InferenceChatChunk> chunkConsumer,
            BooleanSupplier cancellationSupplier) throws Exception {
        String body = chatBody(request, true);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", authHeader())
                .timeout(timeout())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() != 200) {
            StringBuilder errorBody = new StringBuilder();
            try (java.util.stream.Stream<String> lines = response.body()) {
                lines.limit(5).forEach(line -> errorBody.append(line).append(' '));
            }
            throw new IllegalStateException("Streaming chat failed: HTTP "
                    + response.statusCode() + " " + errorBody.toString().trim());
        }
        try (java.util.stream.Stream<String> lines = response.body()) {
            java.util.concurrent.atomic.AtomicBoolean doneSent =
                    new java.util.concurrent.atomic.AtomicBoolean();
            lines.forEach(line -> {
                if (cancellationSupplier != null && cancellationSupplier.getAsBoolean()) {
                    lines.close();
                    return;
                }
                if (line == null || !line.startsWith("data:")) {
                    return;
                }
                String data = line.substring("data:".length()).trim();
                if (data.equals("[DONE]")) {
                    if (doneSent.compareAndSet(false, true)) {
                        chunkConsumer.accept(InferenceChatChunk.done(engineId));
                    }
                    return;
                }
                try {
                    JsonNode node = mapper.readTree(data);
                    String delta = node.path("choices").path(0).path("delta")
                            .path("content").asText("");
                    if (!delta.isEmpty()) {
                        chunkConsumer.accept(new InferenceChatChunk(delta, false, engineId));
                    }
                    String finish = node.path("choices").path(0)
                            .path("finish_reason").asText("");
                    if (!finish.isBlank() && doneSent.compareAndSet(false, true)) {
                        chunkConsumer.accept(InferenceChatChunk.done(engineId));
                    }
                } catch (Exception ignored) {
                    // non-JSON keep-alive line: skip
                }
            });
        }
    }

    private String chatBody(InferenceChatRequest request, boolean stream) throws Exception {
        StringBuilder messages = new StringBuilder();
        for (InferenceChatMessage message : request.messages()) {
            if (messages.length() > 0) {
                messages.append(',');
            }
            messages.append(mapper.writeValueAsString(Map.of(
                    "role", message.role(), "content", message.content())));
        }
        return "{\"model\":\"" + escape(request.model())
                + "\",\"stream\":" + stream + ",\"messages\":[" + messages + "]"
                + ",\"temperature\":" + request.temperature()
                + ",\"max_tokens\":" + Math.max(1, request.maxTokens()) + "}";
    }

    private HttpResponse<String> send(String method, String url, String body, Duration timeout)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(timeout);
        String auth = authHeader();
        if (!auth.isEmpty()) {
            builder.header("Authorization", auth);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String authHeader() {
        String key = config.getOrDefault(CONFIG_API_KEY, "");
        return key.isBlank() ? "" : "Bearer " + key;
    }

    private static String errorSnippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String snippet = body.length() > 200 ? body.substring(0, 200) : body;
        return snippet.replace('\n', ' ');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
