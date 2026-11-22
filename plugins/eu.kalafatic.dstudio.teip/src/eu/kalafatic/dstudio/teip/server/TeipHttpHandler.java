package eu.kalafatic.dstudio.teip.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TeipHttpHandler implements HttpHandler {

    private final TeipExecutionManager manager;
    private final boolean devModeConfigured;
    private final ObjectMapper mapper = new ObjectMapper();

    public TeipHttpHandler(TeipExecutionManager manager, boolean devModeConfigured) {
        this.manager = manager;
        this.devModeConfigured = devModeConfigured;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        Map<String, Object> context = new LinkedHashMap<>();
        try {
            authenticate(exchange);
            route(exchange, method, path);
        } catch (TeipException e) {
            writeError(exchange, e.code(), e.getMessage(), context, e.details());
        } catch (Exception e) {
            writeError(exchange, TeipWire.ErrorCode.INTERNAL_ERROR,
                    "Internal error: " + e.getMessage(), context, Map.of());
        } finally {
            exchange.close();
        }
    }

    private void authenticate(HttpExchange exchange) {
        // Current phase policy: loopback clients are always local-trusted (the
        // embedded browser, the desktop application, CLI and local integrations).
        if (TeipAuth.isLoopback(exchange.getRemoteAddress().getAddress())) {
            return;
        }
        Optional<String> token = TeipAuth.configuredToken();
        if (token.isPresent()) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new TeipException(TeipWire.ErrorCode.AUTHENTICATION_FAILED,
                        "Missing Authorization: Bearer <token> header", Map.of());
            }
            String presented = header.substring("Bearer ".length()).trim();
            if (!constantTimeEquals(token.get(), presented)) {
                throw new TeipException(TeipWire.ErrorCode.AUTHENTICATION_FAILED,
                        "Invalid bearer token", Map.of());
            }
            return;
        }
        throw new TeipException(TeipWire.ErrorCode.AUTHENTICATION_FAILED,
                "TEIP authentication required for non-loopback access: configure "
                        + "dstudio.teip.auth.token", Map.of());
    }

    private boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private void route(HttpExchange exchange, String method, String path) throws IOException {
        if (!path.startsWith("/teip/v1")) {
            writeError(exchange, TeipWire.ErrorCode.NOT_FOUND,
                    "Not found: " + path, Map.of(), Map.of());
            return;
        }
        String rest = path.substring("/teip/v1".length());
        if (rest.isEmpty() || "/".equals(rest)) {
            writeError(exchange, TeipWire.ErrorCode.NOT_FOUND, "Not found", Map.of(),
                    Map.of());
            return;
        }
        if ("/health".equals(rest)) {
            requireMethod(exchange, method, "GET");
            writeJson(exchange, 200, manager.health());
            return;
        }
        if ("/capabilities".equals(rest)) {
            requireMethod(exchange, method, "GET");
            writeJson(exchange, 200, manager.capabilities());
            return;
        }
        if ("/executions".equals(rest)) {
            requireMethod(exchange, method, "POST");
            createExecution(exchange);
            return;
        }
        if (rest.startsWith("/executions/")) {
            String[] segments = rest.substring("/executions/".length()).split("/");
            String executionId = segments[0];
            if (segments.length == 1) {
                requireMethod(exchange, method, "GET");
                writeJson(exchange, 200, manager.status(executionId));
                return;
            }
            switch (segments[1]) {
                case "events" -> {
                    requireMethod(exchange, method, "GET");
                    writeJson(exchange, 200, manager.events(executionId,
                            queryLong(exchange, "afterSequence", 0),
                            queryInt(exchange, "limit", 50)));
                }
                case "result" -> {
                    requireMethod(exchange, method, "GET");
                    writeJson(exchange, 200, manager.result(executionId));
                }
                case "cancel" -> {
                    requireMethod(exchange, method, "POST");
                    writeJson(exchange, 200, manager.cancel(executionId));
                }
                default -> writeError(exchange, TeipWire.ErrorCode.NOT_FOUND,
                        "Not found: " + path, Map.of(), Map.of());
            }
            return;
        }
        writeError(exchange, TeipWire.ErrorCode.NOT_FOUND, "Not found: " + path, Map.of(),
                Map.of());
    }

    private void requireMethod(HttpExchange exchange, String method, String expected) {
        if (!expected.equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", expected);
            throw new TeipException(TeipWire.ErrorCode.METHOD_NOT_ALLOWED,
                    "Method " + method + " not allowed for this endpoint", Map.of());
        }
    }

    private void createExecution(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(
                TeipWire.MAX_REQUEST_BYTES + 1);
        if (body.length > TeipWire.MAX_REQUEST_BYTES) {
            throw new TeipException(TeipWire.ErrorCode.VALIDATION_FAILED,
                    "Request body exceeds 5 MB limit", Map.of("field", "body"));
        }
        TeipWire.ExecutionRequest request = parseRequest(body);
        String idempotencyKey = request.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request = new TeipWire.ExecutionRequest(request.protocol(), request.version(),
                    request.requestId(), request.correlationId(), idempotencyKey,
                    request.timestamp(), request.caller(), request.capability(),
                    request.mode(), request.model(), request.instruction(),
                    request.contextReferences(), request.grantedToolScopes(),
                    request.parameters());
        }
        TeipExecutionManager.CreateOutcome outcome = manager.createExecution(request);
        String executionId = outcome.task().getId();
        Map<String, String> links = new LinkedHashMap<>();
        links.put("status", "/teip/v1/executions/" + executionId);
        links.put("events", "/teip/v1/executions/" + executionId + "/events");
        links.put("result", "/teip/v1/executions/" + executionId + "/result");
        links.put("cancel", "/teip/v1/executions/" + executionId + "/cancel");
        TeipWire.AcceptedResponse response = new TeipWire.AcceptedResponse(
                TeipWire.PROTOCOL, TeipWire.VERSION, executionId,
                outcome.duplicate() ? externalStatus(outcome.task())
                        : "QUEUED",
                request.correlationId(), Instant.now().toString(), links);
        writeJson(exchange, outcome.duplicate() ? 200 : 202, response);
    }

    private String externalStatus(
            eu.kalafatic.dstudio.core.model.ExecutionTask task) {
        return switch (task.getStatus()) {
            case QUEUED -> "QUEUED";
            case RUNNING -> "RUNNING";
            case COMPLETED -> "COMPLETED";
            case FAILED, TIMED_OUT -> "FAILED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private TeipWire.ExecutionRequest parseRequest(byte[] body) {
        JsonNode node;
        try {
            node = mapper.readTree(body == null || body.length == 0
                    ? "{}".getBytes(StandardCharsets.UTF_8) : body);
        } catch (Exception e) {
            throw new TeipException(TeipWire.ErrorCode.VALIDATION_FAILED,
                    "Invalid JSON body: " + e.getMessage(), Map.of("field", "body"));
        }
        String mode = node.path("mode").asText(node.path("aiMode").asText(""));
        String model = node.path("model").asText("");
        List<String> grantedScopes = new ArrayList<>();
        JsonNode scopes = node.has("grantedToolScopes") ? node.get("grantedToolScopes")
                : node.get("mcpToolScopes");
        if (scopes != null && scopes.isArray()) {
            scopes.forEach(s -> grantedScopes.add(s.asText()));
        }
        JsonNode callerNode = node.path("caller");
        String callerSystem = callerNode.path("system")
                .asText(callerNode.path("app").asText(""));
        TeipWire.Caller caller = new TeipWire.Caller(callerSystem,
                callerNode.path("projectId").asText(""),
                callerNode.path("taskId").asText(""),
                callerNode.path("operator").asText(""));
        List<TeipWire.ContextReference> references = new ArrayList<>();
        JsonNode refs = node.path("contextReferences");
        if (refs.isArray()) {
            for (JsonNode ref : refs) {
                if (ref.isTextual()) {
                    references.add(new TeipWire.ContextReference("FILE", ref.asText(), ""));
                } else if (ref.isObject()) {
                    references.add(new TeipWire.ContextReference(
                            ref.path("type").asText("FILE"),
                            ref.path("path").asText(""),
                            ref.path("description").asText("")));
                }
            }
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        JsonNode parametersNode = node.path("parameters");
        if (parametersNode.isObject()) {
            parametersNode.fields().forEachRemaining(e ->
                    parameters.put(e.getKey(), jsonToObject(e.getValue())));
        }
        return new TeipWire.ExecutionRequest(node.path("protocol").asText(""),
                node.path("version").asText(""), node.path("requestId").asText(""),
                node.path("correlationId").asText(""),
                node.path("idempotencyKey").asText(""),
                node.path("timestamp").asText(""), caller,
                node.path("capability").asText(""), mode, model,
                node.path("instruction").asText(""), references, grantedScopes, parameters);
    }

    private Object jsonToObject(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return mapper.convertValue(node, Object.class);
    }

    private long queryLong(HttpExchange exchange, String name, long defaultValue) {
        String raw = exchange.getRequestURI().getQuery() == null ? null
                : java.util.Arrays.stream(
                        exchange.getRequestURI().getQuery().split("&"))
                        .filter(p -> p.startsWith(name + "="))
                        .map(p -> p.substring(name.length() + 1))
                        .findFirst().orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int queryInt(HttpExchange exchange, String name, int defaultValue) {
        String raw = exchange.getRequestURI().getQuery() == null ? null
                : java.util.Arrays.stream(
                        exchange.getRequestURI().getQuery().split("&"))
                        .filter(p -> p.startsWith(name + "="))
                        .map(p -> p.substring(name.length() + 1))
                        .findFirst().orElse(null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object body)
            throws IOException {
        byte[] payload = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private void writeError(HttpExchange exchange, TeipWire.ErrorCode code, String message,
            Map<String, Object> context, Map<String, Object> details) throws IOException {
        TeipWire.ErrorEnvelope envelope = new TeipWire.ErrorEnvelope(TeipWire.PROTOCOL,
                TeipWire.VERSION, code.name(), message,
                context.getOrDefault("requestId", null) instanceof String s ? s : null,
                null, code.retryable(), Instant.now().toString(), details);
        writeJson(exchange, code.httpStatus(), envelope);
    }
}
