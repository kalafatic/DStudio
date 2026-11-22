package eu.kalafatic.dstudio.teip.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineFactory;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceException;
import eu.kalafatic.dstudio.core.inference.InferenceService;
import eu.kalafatic.dstudio.core.inference.InferenceSession;
import eu.kalafatic.dstudio.core.inference.InferenceSessionMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Headless inference-management surface: inference engines, model discovery, model
 * lifecycle, runtime status, direct chat, and (Phase 7) persisted direct-inference
 * sessions with streaming, cancellation and history.
 *
 * <p>Routes (mounted under /api/inference):</p>
 * <ul>
 *   <li>GET /api/inference/engines — registered engines with status</li>
 *   <li>GET /api/inference/models?engine=&lt;id&gt; — model discovery</li>
 *   <li>POST /api/inference/engines/{id}/load|unload {model}</li>
 *   <li>GET /api/inference/engines/{id}/runtime — runtime status</li>
 *   <li>POST /api/inference/chat {engine, model, messages} — one-shot blocking chat</li>
 *   <li>POST /api/inference/sessions {engine?, model, systemPrompt?} — 201 session</li>
 *   <li>GET /api/inference/sessions — list sessions</li>
 *   <li>GET /api/inference/sessions/{id} — session + messages</li>
 *   <li>POST /api/inference/sessions/{id}/messages {content} — blocking answer</li>
 *   <li>POST /api/inference/sessions/{id}/messages/stream {content} — SSE answer</li>
 *   <li>POST /api/inference/sessions/{id}/cancel — idempotent cancel</li>
 * </ul>
 *
 * <p>All routes require the shared API authentication (bearer token or explicit
 * dev-mode loopback opt-out). No route ever returns engine credentials.</p>
 */
public class InferenceHttpHandler implements HttpHandler {

    public static final String PATH = "/api/inference";

    private final InferenceEngineRegistry registry;
    private final InferenceService inferenceService;
    private final InferenceEngineFactory engineFactory;
    private final boolean devModeConfigured;
    private final ObjectMapper mapper = new ObjectMapper();

    public InferenceHttpHandler(InferenceEngineRegistry registry,
            InferenceService inferenceService, InferenceEngineFactory engineFactory,
            boolean devModeConfigured) {
        this.registry = registry;
        this.inferenceService = inferenceService;
        this.engineFactory = engineFactory;
        this.devModeConfigured = devModeConfigured;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiAuth.authenticate(exchange, devModeConfigured);
        } catch (ApiAuth.ApiAuthException e) {
            respond(exchange, 401, error("AUTHENTICATION_FAILED", e.getMessage()));
            return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String rest = path.startsWith(PATH) ? path.substring(PATH.length()) : path;

        if (rest.startsWith("/sessions")) {
            handleSessions(exchange, method, rest);
            return;
        }
        try {
            if ("GET".equals(method) && (rest.isEmpty() || "/engines".equals(rest))) {
                respond(exchange, 200, Map.of("engines", engines()));
                return;
            }
            if ("POST".equals(method) && rest.equals("/engines")) {
                registerEngineInstance(exchange);
                return;
            }
            if ("DELETE".equals(method) && rest.startsWith("/engines/")
                    && !rest.substring("/engines/".length()).contains("/")) {
                removeEngineInstance(exchange,
                        rest.substring("/engines/".length()));
                return;
            }
            if ("GET".equals(method) && rest.equals("/models")) {
                String engineId = query(exchange, "engine");
                InferenceEngineAdapter adapter = resolve(engineId);
                if (adapter == null) {
                    respond(exchange, 404, error("NOT_FOUND", "Unknown inference engine: "
                            + engineId));
                    return;
                }
                List<Map<String, Object>> models = new ArrayList<>();
                for (InferenceEngineAdapter.InferenceModel model : adapter.listModels()) {
                    models.add(Map.of("id", model.id(), "name", model.name(), "provider",
                            model.provider(), "engine", model.engine(), "sizeBytes",
                            model.sizeBytes(), "available", model.available()));
                }
                respond(exchange, 200, Map.of("engine", adapter.getEngineId(),
                        "models", models));
                return;
            }
            if (rest.startsWith("/engines/")) {
                String sub = rest.substring("/engines/".length());
                String[] parts = sub.split("/");
                String engineId = parts[0];
                InferenceEngineAdapter adapter = registry.findAdapter(engineId).orElse(null);
                if (adapter == null) {
                    respond(exchange, 404, error("NOT_FOUND", "Unknown inference engine: "
                            + engineId));
                    return;
                }
                if (parts.length == 2 && "POST".equals(method)
                        && (parts[1].equals("load") || parts[1].equals("unload"))) {
                    String model = mapper.readTree(exchange.getRequestBody())
                            .path("model").asText("");
                    boolean success = parts[1].equals("load") ? adapter.loadModel(model)
                            : adapter.unloadModel(model);
                    respond(exchange, success ? 200 : 404, Map.of("engine", engineId,
                            "model", model, "success", success));
                    return;
                }
                if (parts.length == 2 && "GET".equals(method)
                        && parts[1].equals("runtime")) {
                    InferenceEngineAdapter.InferenceRuntimeStatus status =
                            adapter.runtimeStatus();
                    respond(exchange, 200, Map.of("engine", engineId, "ready", status.ready(),
                            "availableProcessors", status.availableProcessors(),
                            "systemLoadAverage", status.systemLoadAverage(),
                            "maxHeapMb", status.maxHeapMb(), "usedHeapMb", status.usedHeapMb(),
                            "gpu", Map.of("detected", status.gpuDetected(), "device",
                                    status.gpuDevice(), "totalVramMb", status.gpuTotalVramMb(),
                                    "usedVramMb", status.gpuUsedVramMb(), "freeVramMb",
                                    status.gpuFreeVramMb()), "activeEngines",
                            status.activeEngines()));
                    return;
                }
            }
            if ("POST".equals(method) && rest.equals("/chat")) {
                JsonNode body = mapper.readTree(exchange.getRequestBody());
                String engineId = body.path("engine").asText("");
                InferenceEngineAdapter adapter = resolve(engineId);
                if (adapter == null) {
                    respond(exchange, 404, error("NOT_FOUND", "Unknown inference engine: "
                            + engineId));
                    return;
                }
                List<InferenceEngineAdapter.InferenceChatMessage> messages = new ArrayList<>();
                for (JsonNode message : body.path("messages")) {
                    messages.add(new InferenceEngineAdapter.InferenceChatMessage(
                            message.path("role").asText("user"),
                            message.path("content").asText("")));
                }
                InferenceEngineAdapter.InferenceChatResponse response = adapter.chat(
                        new InferenceEngineAdapter.InferenceChatRequest(
                                body.path("model").asText(""), messages,
                                body.path("temperature").asDouble(0.7),
                                body.path("maxTokens").asInt(512)));
                respond(exchange, 200, Map.of("engine", adapter.getEngineId(), "content",
                        response.content(), "finishReason", response.finishReason(),
                        "model", response.model(), "durationMs", response.durationMs()));
                return;
            }
            respond(exchange, 404, error("NOT_FOUND", "Unknown inference route: " + path));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, error("VALIDATION_FAILED", e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, error("INTERNAL_ERROR", e.getClass().getSimpleName()
                    + ": " + e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    /**
     * POST /api/inference/engines {type, id?, name?, config?} — registers a new runtime
     * instance (multi-instance support: several endpoints of the same provider type can
     * coexist under distinct ids with independent configuration).
     */
    private void registerEngineInstance(HttpExchange exchange) throws IOException {
        if (engineFactory == null) {
            respond(exchange, 422, error("CAPABILITY_UNSUPPORTED",
                    "Engine instance creation is not available in this runtime"));
            return;
        }
        JsonNode body = mapper.readTree(exchange.getRequestBody());
        String type = body.path("type").asText("").toUpperCase();
        if (type.isBlank()) {
            respond(exchange, 400, error("VALIDATION_FAILED",
                    "Engine type is required (OLLAMA, ENR, OPENAI_COMPATIBLE, ANTHROPIC)"));
            return;
        }
        String id = body.path("id").asText("").trim();
        if (id.isBlank()) {
            id = type.toLowerCase();
        }
        if (registry.findEngine(id).isPresent()) {
            respond(exchange, 409, error("CONFLICT", "Inference engine instance '"
                    + id + "' is already registered; provide an explicit id"));
            return;
        }
        String name = body.path("name").asText("").trim();
        if (name.isBlank()) {
            name = id;
        }
        Map<String, String> configuration = new java.util.LinkedHashMap<>();
        JsonNode config = body.path("config");
        if (config.isObject()) {
            config.fields().forEachRemaining(entry ->
                    configuration.put(entry.getKey(), entry.getValue().asText("")));
        }
        var adapter = engineFactory.createEngine(type, id, name, configuration);
        if (adapter.isEmpty()) {
            respond(exchange, 422, error("CAPABILITY_UNSUPPORTED",
                    "Unknown engine type: " + type));
            return;
        }
        registry.registerEngine(adapter.get());
        if (!configuration.isEmpty()) {
            registry.updateConfiguration(id, configuration);
        }
        respond(exchange, 201, Map.of("id", id, "type", type, "name", name,
                "registered", true));
    }

    /** DELETE /api/inference/engines/{id} — removes an instance permanently. */
    private void removeEngineInstance(HttpExchange exchange, String engineId)
            throws IOException {
        if (engineFactory == null) {
            respond(exchange, 422, error("CAPABILITY_UNSUPPORTED",
                    "Engine instance removal is not available in this runtime"));
            return;
        }
        if (registry.findEngine(engineId).isEmpty()) {
            respond(exchange, 404, error("NOT_FOUND",
                    "Unknown inference engine: " + engineId));
            return;
        }
        registry.removeEngine(engineId);
        respond(exchange, 200, Map.of("id", engineId, "removed", true));
    }

    private void handleSessions(HttpExchange exchange, String method, String rest)
            throws IOException {
        try {
            if (rest.equals("/sessions") && "POST".equals(method)) {
                JsonNode body = mapper.readTree(exchange.getRequestBody());
                InferenceSession session = inferenceService.createSession(
                        body.path("engine").asText(""),
                        body.path("model").asText(""),
                        body.path("systemPrompt").asText(""),
                        body.path("title").asText(null));
                respond(exchange, 201, sessionWire(session));
                return;
            }
            if (rest.equals("/sessions") && "GET".equals(method)) {
                List<Map<String, Object>> sessions = new ArrayList<>();
                inferenceService.listSessions()
                        .forEach(session -> sessions.add(sessionWire(session)));
                respond(exchange, 200, Map.of("sessions", sessions));
                return;
            }
            String sub = rest.substring("/sessions".length());
            String[] parts = sub.split("/");
            if (parts.length >= 2) {
                String sessionId = parts[1];
                if (parts.length == 2 && "GET".equals(method)) {
                    InferenceSession session = inferenceService.getSession(sessionId)
                            .orElse(null);
                    if (session == null) {
                        respond(exchange, 404, error("NOT_FOUND",
                                "Unknown inference session: " + sessionId));
                        return;
                    }
                    Map<String, Object> wire = sessionWire(session);
                    List<Map<String, Object>> messages = new ArrayList<>();
                    for (InferenceSessionMessage message
                            : inferenceService.getMessages(sessionId)) {
                        messages.add(Map.of("id", message.id(), "role", message.role(),
                                "content", message.content(), "createdAt",
                                message.createdAt().toString()));
                    }
                    wire.put("messages", messages);
                    respond(exchange, 200, wire);
                    return;
                }
                if (parts.length == 3 && "POST".equals(method)
                        && parts[2].equals("rename")) {
                    JsonNode body = mapper.readTree(exchange.getRequestBody());
                    InferenceSession renamed = inferenceService.renameSession(sessionId,
                            body.path("title").asText(""));
                    respond(exchange, 200, sessionWire(renamed));
                    return;
                }
                if (parts.length == 2 && "DELETE".equals(method)) {
                    inferenceService.deleteSession(sessionId);
                    respond(exchange, 200, Map.of("sessionId", sessionId, "deleted", true));
                    return;
                }
                if (parts.length == 3 && "POST".equals(method)
                        && parts[2].equals("cancel")) {
                    if (inferenceService.getSession(sessionId).isEmpty()) {
                        respond(exchange, 404, error("NOT_FOUND",
                                "Unknown inference session: " + sessionId));
                        return;
                    }
                    inferenceService.cancel(sessionId);
                    respond(exchange, 200, Map.of("sessionId", sessionId, "cancelled", true));
                    return;
                }
                if (parts.length == 3 && "POST".equals(method)
                        && parts[2].equals("messages")) {
                    JsonNode body = mapper.readTree(exchange.getRequestBody());
                    InferenceService.SendResult result = inferenceService.sendMessage(
                            sessionId, body.path("content").asText(""));
                    respond(exchange, 200, Map.of("sessionId", sessionId, "content",
                            result.content(), "finishReason", result.finishReason(),
                            "model", result.model(), "engine", result.engine(),
                            "durationMs", result.durationMs()));
                    return;
                }
                if (parts.length == 4 && "POST".equals(method)
                        && parts[2].equals("messages") && parts[3].equals("stream")) {
                    streamMessage(exchange, sessionId);
                    return;
                }
            }
            respond(exchange, 404, error("NOT_FOUND", "Unknown inference session route: "
                    + rest));
        } catch (InferenceException e) {
            respond(exchange, statusFor(e), error(e.code().name(), e.getMessage()));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, error("VALIDATION_FAILED", e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, error("INTERNAL_ERROR", e.getClass().getSimpleName()
                    + ": " + e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void streamMessage(HttpExchange exchange, String sessionId) throws IOException {
        JsonNode body = mapper.readTree(exchange.getRequestBody());
        String content = body.path("content").asText("");
        try {
            SseSupport.startSse(exchange);
        } catch (IOException e) {
            exchange.close();
            return;
        }
        try (OutputStream out = exchange.getResponseBody()) {
            try {
                inferenceService.streamMessage(sessionId, content, chunk -> {
                    try {
                        if (chunk.done()) {
                            SseSupport.writeEvent(out, "", "done",
                                    mapper.writeValueAsString(Map.of("sessionId", sessionId,
                                            "done", true)));
                        } else {
                            SseSupport.writeEvent(out, "", "chunk",
                                    mapper.writeValueAsString(Map.of("sessionId", sessionId,
                                            "text", chunk.text())));
                        }
                    } catch (IOException e) {
                        // client disconnected: stop consuming the engine stream
                        inferenceService.cancel(sessionId);
                    }
                });
                SseSupport.writeComment(out, "complete");
            } catch (InferenceException e) {
                SseSupport.writeEvent(out, "", "error",
                        mapper.writeValueAsString(Map.of("code", e.code().name(),
                                "message", e.getMessage(), "sessionId", sessionId)));
            }
        }
    }

    private Map<String, Object> sessionWire(InferenceSession session) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("id", session.id());
        wire.put("engineId", session.engineId());
        wire.put("model", session.model());
        wire.put("systemPrompt", session.systemPrompt());
        wire.put("status", session.status().name());
        wire.put("statusMessage", session.statusMessage());
        wire.put("createdAt", session.createdAt().toString());
        wire.put("updatedAt", session.updatedAt().toString());
        wire.put("title", session.title());
        return wire;
    }

    private static int statusFor(InferenceException e) {
        return switch (e.code()) {
            case NOT_FOUND -> 404;
            case CONFLICT -> 409;
            case VALIDATION_FAILED -> 400;
            case INFERENCE_FAILED -> 502;
        };
    }

    private InferenceEngineAdapter resolve(String engineId) {
        if (engineId == null || engineId.isBlank()) {
            Optional<InferenceEngineAdapter> selected =
                    registry.findSelectedDefaultAdapter();
            return selected.orElse(null);
        }
        return registry.findAdapter(engineId).orElse(null);
    }

    private List<Map<String, Object>> engines() {
        List<Map<String, Object>> engines = new ArrayList<>();
        registry.listEngines().forEach(engine -> engines.add(Map.of("id", engine.getId(),
                "name", engine.getName(), "type", engine.getType(), "enabled",
                engine.isEnabled(), "status", engine.getStatus().name(), "default",
                registry.getSelectedDefaultEngineId().map(engine.getId()::equals)
                        .orElse(false))));
        return engines;
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name)) {
                return URLDecoder.decode(pair.substring(equals + 1),
                        StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> body)
            throws IOException {
        byte[] payload = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
        exchange.close();
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", code);
        envelope.put("message", message == null ? "" : message);
        return envelope;
    }
}
