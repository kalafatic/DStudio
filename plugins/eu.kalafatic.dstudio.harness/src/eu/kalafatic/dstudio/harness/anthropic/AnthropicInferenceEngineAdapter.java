package eu.kalafatic.dstudio.harness.anthropic;

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
 * Anthropic inference-engine adapter (Phase 7, S3). Consumes the Anthropic Messages wire
 * contract ({@code /v1/models}, {@code /v1/messages} incl. streaming) with the
 * {@code x-api-key} / {@code anthropic-version} headers. Multiple instances (distinct
 * engine ids) can be registered for different endpoints.
 *
 * <p><b>Verification status:</b> wire-level tests run against an in-process stub;
 * live inference requires a real API key and has not been executed on this host. The
 * live test skips silently when no key is configured.</p>
 */
public class AnthropicInferenceEngineAdapter implements InferenceEngineAdapter {

    public static final String DEFAULT_ENGINE_ID = "anthropic";
    public static final String DEFAULT_ENGINE_NAME = "Anthropic";
    public static final String ENGINE_TYPE = "ANTHROPIC";

    public static final String CONFIG_ENDPOINT = "endpoint";
    public static final String CONFIG_API_KEY = "apiKey";
    public static final String CONFIG_DEFAULT_MODEL = "defaultModel";
    public static final String CONFIG_TIMEOUT_SECONDS = "timeoutSeconds";
    public static final String CONFIG_ANTHROPIC_VERSION = "anthropicVersion";

    public static final String DEFAULT_ENDPOINT = "https://api.anthropic.com";
    public static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

    private final String engineId;
    private final String engineName;
    private final Map<String, String> config = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    public AnthropicInferenceEngineAdapter() {
        this(DEFAULT_ENGINE_ID, DEFAULT_ENGINE_NAME);
    }

    public AnthropicInferenceEngineAdapter(String engineId, String engineName) {
        this.engineId = engineId;
        this.engineName = engineName;
        config.put(CONFIG_ENDPOINT, DEFAULT_ENDPOINT);
        config.put(CONFIG_API_KEY, "");
        config.put(CONFIG_DEFAULT_MODEL, "");
        config.put(CONFIG_TIMEOUT_SECONDS, "120");
        config.put(CONFIG_ANTHROPIC_VERSION, DEFAULT_ANTHROPIC_VERSION);
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
            HttpResponse<String> response = send("GET", endpoint() + "/v1/models", null,
                    timeout());
            if (response.statusCode() == 200) {
                return InferenceConnectionTestResult.ok("Anthropic API available at "
                        + endpoint());
            }
            String hint = response.statusCode() == 401
                    ? " (invalid or missing apiKey)" : "";
            return InferenceConnectionTestResult.error("Anthropic API at " + endpoint()
                    + " returned HTTP " + response.statusCode() + hint);
        } catch (Exception e) {
            return InferenceConnectionTestResult.error("Anthropic API unreachable at "
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
            HttpResponse<String> response = send("GET", endpoint() + "/v1/models", null,
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
                models.add(new InferenceModel(id, id, "anthropic", engineId, 0, true));
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
        // No runtime/health endpoint in the Anthropic contract: report connectivity only.
        return new InferenceRuntimeStatus(testConnection().success(), 0, 0, 0, 0,
                false, "", 0, 0, 0, List.of(engineId));
    }

    @Override
    public boolean loadModel(String modelId) {
        return false; // not supported by the Anthropic API
    }

    @Override
    public boolean unloadModel(String modelId) {
        return false; // not supported by the Anthropic API
    }

    @Override
    public InferenceChatResponse chat(InferenceChatRequest request) {
        try {
            String body = messagesBody(request, false);
            long start = System.currentTimeMillis();
            HttpResponse<String> response = send("POST", endpoint() + "/v1/messages",
                    body, timeout());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Chat failed: HTTP " + response.statusCode()
                        + " " + errorSnippet(response.body()));
            }
            JsonNode node = mapper.readTree(response.body());
            String content = collectText(node.path("content"));
            return new InferenceChatResponse(content,
                    node.path("stop_reason").asText("end_turn"), engineId,
                    node.path("model").asText(request.model()),
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
        String body = messagesBody(request, true);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint() + "/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.getOrDefault(CONFIG_API_KEY, ""))
                .header("anthropic-version", config.getOrDefault(CONFIG_ANTHROPIC_VERSION,
                        DEFAULT_ANTHROPIC_VERSION))
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
            lines.forEach(line -> {
                if (cancellationSupplier != null && cancellationSupplier.getAsBoolean()) {
                    lines.close();
                    return;
                }
                if (line == null || !line.startsWith("data:")) {
                    return;
                }
                try {
                    JsonNode node = mapper.readTree(
                            line.substring("data:".length()).trim());
                    String eventType = node.path("type").asText("");
                    if ("content_block_delta".equals(eventType)) {
                        String text = node.path("delta").path("text").asText("");
                        if (!text.isEmpty()) {
                            chunkConsumer.accept(new InferenceChatChunk(text, false,
                                    engineId));
                        }
                    } else if ("message_stop".equals(eventType)) {
                        chunkConsumer.accept(InferenceChatChunk.done(engineId));
                    }
                } catch (Exception ignored) {
                    // non-JSON line: skip
                }
            });
        }
    }

    private String messagesBody(InferenceChatRequest request, boolean stream)
            throws Exception {
        StringBuilder messages = new StringBuilder();
        StringBuilder system = new StringBuilder();
        for (InferenceChatMessage message : request.messages()) {
            if ("system".equals(message.role())) {
                if (system.length() > 0) {
                    system.append('\n');
                }
                system.append(message.content());
                continue;
            }
            if (messages.length() > 0) {
                messages.append(',');
            }
            messages.append(mapper.writeValueAsString(Map.of(
                    "role", message.role(), "content", message.content())));
        }
        String body = "{\"model\":\"" + escape(request.model())
                + "\",\"stream\":" + stream + ",\"max_tokens\":"
                + Math.max(1, request.maxTokens())
                + ",\"temperature\":" + request.temperature()
                + ",\"messages\":[" + messages + "]";
        if (system.length() > 0) {
            body += ",\"system\":\"" + escape(system.toString()) + "\"";
        }
        return body + "}";
    }

    private HttpResponse<String> send(String method, String url, String body, Duration timeout)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.getOrDefault(CONFIG_API_KEY, ""))
                .header("anthropic-version", config.getOrDefault(CONFIG_ANTHROPIC_VERSION,
                        DEFAULT_ANTHROPIC_VERSION))
                .timeout(timeout);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String collectText(JsonNode content) {
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText(""))) {
                text.append(block.path("text").asText(""));
            }
        }
        return text.toString();
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
