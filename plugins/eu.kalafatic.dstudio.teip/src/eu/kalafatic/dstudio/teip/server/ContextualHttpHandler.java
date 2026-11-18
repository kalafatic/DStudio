package eu.kalafatic.dstudio.teip.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import eu.kalafatic.dstudio.core.agent.AgentProfileRegistry;
import eu.kalafatic.dstudio.core.contextual.ActionFeedback;
import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionHandler;
import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.config.DStudioConfiguration;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineFactory;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.service.ConfigurationService;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.core.service.ProjectService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Web surface of the unified contextual action framework (Darwin Family Contextual
 * Action Contract v1.0): resolves the same actions as the RCP menus and executes them
 * through the same single registry.
 *
 * <ul>
 *   <li>GET /api/contextual/actions?type=…&amp;id=… — resolved actions for the selection</li>
 *   <li>POST /api/contextual/execute — executes one action id on the selection</li>
 * </ul>
 *
 * The handler also registers the WEB-surface handlers (JSON configuration, agent
 * import/export, execution submission) on the registry.
 */
public class ContextualHttpHandler implements HttpHandler {

    public static final String PATH = "/api/contextual";

    private final ContextualActionRegistry registry;
    private final HarnessRegistry harnessRegistry;
    private final InferenceEngineRegistry engineRegistry;
    private final InferenceEngineFactory engineFactory;
    private final AgentProfileRegistry agentRegistry;
    private final ConfigurationService configurationService;
    private final ExecutionService executionService;
    private final ProjectService projectService;
    private final boolean devModeConfigured;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean webHandlersRegistered = false;

    public ContextualHttpHandler(ContextualActionRegistry registry,
            HarnessRegistry harnessRegistry, InferenceEngineRegistry engineRegistry,
            InferenceEngineFactory engineFactory, AgentProfileRegistry agentRegistry,
            ConfigurationService configurationService, ExecutionService executionService,
            ProjectService projectService, boolean devModeConfigured) {
        this.registry = registry;
        this.harnessRegistry = harnessRegistry;
        this.engineRegistry = engineRegistry;
        this.engineFactory = engineFactory;
        this.agentRegistry = agentRegistry;
        this.configurationService = configurationService;
        this.executionService = executionService;
        this.projectService = projectService;
        this.devModeConfigured = devModeConfigured;
        registerWebHandlers();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiAuth.authenticate(exchange, devModeConfigured);
        } catch (ApiAuth.ApiAuthException e) {
            respond(exchange, 401, Map.of("ok", false, "error", "AUTHENTICATION_FAILED",
                    "message", e.getMessage()));
            return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String rest = path.startsWith(PATH) ? path.substring(PATH.length()) : path;
        try {
            if ("GET".equals(method) && rest.equals("/actions")) {
                handleResolve(exchange);
                return;
            }
            if ("POST".equals(method) && rest.equals("/execute")) {
                handleExecute(exchange);
                return;
            }
            respond(exchange, 404, Map.of("ok", false, "error", "NOT_FOUND",
                    "message", "Unknown contextual endpoint"));
        } catch (Exception e) {
            respond(exchange, 500, Map.of("ok", false, "error", "INTERNAL",
                    "message", e.getMessage() == null ? e.getClass().getSimpleName()
                            : e.getMessage()));
        }
    }

    private void handleResolve(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange);
        ContextSelection selection = selectionFrom(query);
        if (registry == null) {
            respond(exchange, 200, Map.of("contractVersion",
                    ContextActionIds.CONTRACT_VERSION, "actions", List.of()));
            return;
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (ContextualAction action : registry.resolve(selection)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("actionId", action.actionId());
            entry.put("label", action.descriptor().label());
            entry.put("description", action.descriptor().description());
            entry.put("group", action.descriptor().group());
            entry.put("enabled", action.enabled());
            entry.put("disabledReason", action.disabledReason());
            entry.put("confirmation",
                    action.descriptor().confirmation().name().toLowerCase());
            entry.put("dangerous", action.descriptor().dangerous());
            actions.add(entry);
        }
        respond(exchange, 200, Map.of("contractVersion", ContextActionIds.CONTRACT_VERSION,
                "actions", actions));
    }

    private void handleExecute(HttpExchange exchange) throws IOException {
        JsonNode body = mapper.readTree(exchange.getRequestBody());
        String actionId = body.path("actionId").asText("");
        if (actionId.isBlank()) {
            respond(exchange, 400, Map.of("ok", false, "error", "VALIDATION_FAILED",
                    "message", "actionId is required"));
            return;
        }
        ContextSelection selection = selectionFrom(body);
        if (registry == null) {
            respond(exchange, 500, Map.of("ok", false, "error", "INTERNAL",
                    "message", "Contextual action registry unavailable"));
            return;
        }
        var result = registry.execute(actionId, selection);
        respond(exchange, 200, Map.of("ok", result.ok(), "actionId", result.actionId(),
                "message", result.message(), "error", result.errorCode()));
    }

    private ContextSelection selectionFrom(Map<String, String> query) {
        ContextSelection.Builder builder = ContextSelection.builder(
                ContextSelection.APPLICATION_DSTUDIO, ContextSelection.SURFACE_WEB,
                query.getOrDefault("type", ""));
        builder.object(query.getOrDefault("id", ""), query.getOrDefault("label", ""))
                .projectId(query.getOrDefault("projectId", ""))
                .sessionId(query.getOrDefault("sessionId", ""))
                .harnessId(query.getOrDefault("harnessId", ""))
                .providerId(query.getOrDefault("providerId", ""))
                .modelId(query.getOrDefault("modelId", ""))
                .agentId(query.getOrDefault("agentId", ""))
                .user(query.getOrDefault("user", ""));
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry.getKey().startsWith("state.")) {
                builder.state(entry.getKey().substring("state.".length()),
                        entry.getValue());
            }
        }
        // Enrich known-object states from the live registries so web availability
        // matches RCP exactly.
        enrich(builder, query.getOrDefault("type", ""), query.getOrDefault("id", ""));
        return builder.build();
    }

    private ContextSelection selectionFrom(JsonNode body) {
        ContextSelection.Builder builder = ContextSelection.builder(
                ContextSelection.APPLICATION_DSTUDIO, ContextSelection.SURFACE_WEB,
                body.path("objectType").asText(""));
        builder.object(body.path("objectId").asText(""),
                body.path("objectLabel").asText(""))
                .projectId(body.path("projectId").asText(""))
                .sessionId(body.path("sessionId").asText(""))
                .harnessId(body.path("harnessId").asText(""))
                .providerId(body.path("providerId").asText(""))
                .modelId(body.path("modelId").asText(""))
                .agentId(body.path("agentId").asText(""))
                .user(body.path("user").asText(""));
        JsonNode stateNode = body.path("objectState");
        if (stateNode.isObject()) {
            stateNode.fields().forEachRemaining(entry -> builder.state(entry.getKey(),
                    entry.getValue().asText()));
        }
        enrich(builder, body.path("objectType").asText(""),
                body.path("objectId").asText(""));
        return builder.build();
    }

    private void enrich(ContextSelection.Builder builder, String objectType,
            String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return;
        }
        switch (objectType) {
            case "harness" -> {
                if (harnessRegistry != null) {
                    harnessRegistry.findHarness(objectId).ifPresent(descriptor -> {
                        builder.state("enabled", descriptor.isEnabled());
                        builder.state("isDefault", harnessRegistry
                                .getSelectedDefaultHarnessId().map(objectId::equals)
                                .orElse(false));
                        builder.capabilities(new java.util.LinkedHashSet<>(
                                descriptor.getCapabilities()));
                    });
                }
            }
            case "provider" -> {
                if (engineRegistry != null) {
                    engineRegistry.findEngine(objectId).ifPresent(descriptor -> {
                        builder.state("enabled", descriptor.isEnabled());
                        builder.state("isDefault", engineRegistry
                                .getSelectedDefaultEngineId().map(objectId::equals)
                                .orElse(false));
                        builder.capability("model:load");
                        builder.capability("model:unload");
                    });
                }
            }
            case "model" -> {
                String defaultModel = configurationService == null ? ""
                        : configurationService.getConfiguration().defaultModel();
                builder.state("isDefault", objectId.equals(defaultModel));
            }
            case "agent" -> {
                if (agentRegistry != null) {
                    agentRegistry.findProfile(objectId).ifPresent(profile -> builder
                            .state("isDefault", agentRegistry.getDefaultProfileId()
                                    .map(objectId::equals).orElse(false)));
                }
            }
            case "execution" -> {
                if (executionService != null) {
                    Optional<ExecutionTask> task = executionService.getTask(objectId);
                    task.ifPresent(t -> builder.state("status", t.getStatus().name()));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ WEB handlers

    private void registerWebHandlers() {
        if (registry == null || webHandlersRegistered) {
            return;
        }
        webHandlersRegistered = true;
        registry.registerHandler(web(ContextActionIds.HARNESS_CONFIGURE, (sel, fb) -> {
            Map<String, String> config = parseStringMap(sel.state("configJson"));
            if (config.isEmpty()) {
                fb.error("No configuration provided", null);
                return;
            }
            harnessRegistry.updateConfiguration(sel.objectId(), config);
            fb.info("Configuration updated");
        }));
        registry.registerHandler(web(ContextActionIds.PROVIDER_CONFIGURE, (sel, fb) -> {
            Map<String, String> config = parseStringMap(sel.state("configJson"));
            if (config.isEmpty()) {
                fb.error("No configuration provided", null);
                return;
            }
            engineRegistry.updateConfiguration(sel.objectId(), config);
            fb.info("Configuration updated");
        }));
        registry.registerHandler(web(ContextActionIds.PROVIDER_REGISTRY_ADD, (sel, fb) -> {
            if (engineFactory == null) {
                fb.error("Engine instance creation is not available in this runtime",
                        null);
                return;
            }
            String type = sel.state("type");
            String id = sel.state("id");
            String name = sel.state("name");
            if (type.isBlank() || id.isBlank()) {
                fb.error("Provider type and id are required", null);
                return;
            }
            Map<String, String> config = parseStringMap(sel.state("configJson"));
            Optional<InferenceEngineAdapter> engine = engineFactory.createEngine(
                    type.toUpperCase(), id, name.isBlank() ? id : name, config);
            if (engine.isEmpty()) {
                fb.error("Unknown provider type: " + type, null);
                return;
            }
            engineRegistry.registerEngine(engine.get());
            fb.info("Provider '" + id + "' registered");
        }));
        registry.registerHandler(web(ContextActionIds.MODEL_INFO, (sel, fb) -> {
            fb.info("Model: " + sel.objectLabel() + " (id " + sel.objectId() + ")");
        }));
        registry.registerHandler(web(ContextActionIds.AGENT_REGISTRY_CREATE, (sel, fb) -> {
            AgentProfile profile = parseProfile(sel.state("profileJson"));
            agentRegistry.createProfile(profile);
            fb.info("Agent created: " + profile.getId());
        }));
        registry.registerHandler(web(ContextActionIds.AGENT_REGISTRY_IMPORT, (sel, fb) -> {
            AgentProfile profile = parseProfile(sel.state("profileJson"));
            agentRegistry.createProfile(profile);
            fb.info("Agent imported: " + profile.getId());
        }));
        registry.registerHandler(web(ContextActionIds.AGENT_EDIT, (sel, fb) -> {
            AgentProfile profile = parseProfile(sel.state("profileJson"));
            agentRegistry.updateProfile(profile);
            fb.info("Agent updated");
        }));
        registry.registerHandler(web(ContextActionIds.AGENT_EXPORT, (sel, fb) -> {
            Optional<AgentProfile> profile = agentRegistry.findProfile(sel.objectId());
            if (profile.isEmpty()) {
                fb.error("Agent not found", null);
                return;
            }
            fb.info(profileToJson(profile.get()));
        }));
        registry.registerHandler(web(ContextActionIds.AGENT_COMPATIBILITY, (sel, fb) -> {
            var result = agentRegistry.checkCompatibility(sel.objectId(),
                    sel.harnessId(), sel.providerId(), sel.modelId());
            fb.info(result.compatible() ? "Compatible with the selected environment."
                    : result.message());
        }));
        registry.registerHandler(web(ContextActionIds.SESSION_DETAILS, (sel, fb) -> {
            Optional<ExecutionTask> task = executionService.getTask(sel.objectId());
            if (task.isEmpty()) {
                fb.error("Execution not found", null);
                return;
            }
            ExecutionTask t = task.get();
            fb.info("Id: " + t.getId() + " | Status: " + t.getStatus()
                    + " | Harness: " + t.getHarnessId() + " | Project: "
                    + t.getProjectId() + " | Model: " + t.getModel()
                    + " | Agent: " + t.getAgentProfileId() + " | Instruction: "
                    + t.getInstruction());
        }));
        registry.registerHandler(web(ContextActionIds.SESSION_HISTORY, (sel, fb) -> {
            List<eu.kalafatic.dstudio.core.model.ExecutionEvent> events =
                    executionService.getEvents(sel.objectId(), 0, 50);
            StringBuilder text = new StringBuilder("Events (" + events.size()
                    + "):\n");
            for (eu.kalafatic.dstudio.core.model.ExecutionEvent event : events) {
                text.append(event.type()).append(" @ ").append(event.timestamp())
                        .append(" — ").append(event.message() == null ? ""
                                : event.message())
                        .append("\n");
            }
            fb.info(text.toString());
        }));
        registry.registerHandler(web(ContextActionIds.PROJECT_CREATE_SESSION, (sel, fb) -> {
            String harnessId = sel.state("harness");
            if (harnessId.isBlank()) {
                harnessId = harnessRegistry.getSelectedDefaultHarnessId().orElse("");
            }
            if (harnessId.isBlank()) {
                fb.error("No harness selected", null);
                return;
            }
            if (projectService == null || projectService.findProject(sel.objectId())
                    .isEmpty()) {
                fb.error("Project not found", null);
                return;
            }
            String instruction = sel.state("instruction");
            if (instruction.isBlank()) {
                fb.error("Instruction is required", null);
                return;
            }
            String model = sel.state("model");
            String agent = sel.state("agent");
            String workingDirectory = projectService.findProject(sel.objectId())
                    .get().rootPath();
            ExecutionService.ExecutionRequest request =
                    new ExecutionService.ExecutionRequest(harnessId, sel.objectId(),
                            "web-" + UUID.randomUUID(), instruction, instruction, model,
                            agent, true, workingDirectory, java.time.Duration.ofMinutes(30));
            ExecutionTask task = executionService.submit(request);
            fb.info("Execution submitted: " + task.getId());
        }));
    }

    private static ContextualActionHandler web(String actionId, Body body) {
        return new ContextualActionHandler() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String surface() {
                return ContextSelection.SURFACE_WEB;
            }

            @Override
            public void execute(ContextSelection selection, ActionFeedback feedback)
                    throws Exception {
                body.run(selection, feedback);
            }
        };
    }

    private interface Body {
        void run(ContextSelection selection, ActionFeedback feedback) throws Exception;
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, String> parseStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = mapper.readTree(json);
            Map<String, String> result = new LinkedHashMap<>();
            if (node.isObject()) {
                node.fields().forEachRemaining(entry -> result.put(entry.getKey(),
                        entry.getValue().asText()));
            }
            return result;
        } catch (IOException e) {
            return Map.of();
        }
    }

    private AgentProfile parseProfile(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Profile JSON is required");
        }
        try {
            JsonNode node = mapper.readTree(json);
            String id = node.path("id").asText("");
            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            List<String> toolPermissions = new ArrayList<>();
            node.path("toolPermissions").forEach(item -> toolPermissions.add(item.asText()));
            List<String> restrictions = new ArrayList<>();
            node.path("restrictions").forEach(item -> restrictions.add(item.asText()));
            Map<String, String> executionParameters = new LinkedHashMap<>();
            JsonNode params = node.path("executionParameters");
            if (params.isObject()) {
                params.fields().forEachRemaining(entry -> executionParameters.put(
                        entry.getKey(), entry.getValue().asText()));
            }
            return new AgentProfile(id, node.path("name").asText(""),
                    node.path("description").asText(""), node.path("role").asText(""),
                    node.path("systemInstructions").asText(""), toolPermissions,
                    restrictions, node.path("preferredHarnessId").asText(""),
                    node.path("preferredProviderId").asText(""),
                    node.path("preferredModel").asText(""), executionParameters,
                    Instant.now(), Instant.now());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid profile JSON: " + e.getMessage());
        }
    }

    private String profileToJson(AgentProfile profile) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", profile.getId());
        map.put("name", profile.getName());
        map.put("description", profile.getDescription());
        map.put("role", profile.getRole());
        map.put("systemInstructions", profile.getSystemInstructions());
        map.put("toolPermissions", profile.getToolPermissions());
        map.put("restrictions", profile.getRestrictions());
        map.put("preferredHarnessId", profile.getPreferredHarnessId());
        map.put("preferredProviderId", profile.getPreferredProviderId());
        map.put("preferredModel", profile.getPreferredModel());
        map.put("executionParameters", profile.getExecutionParameters());
        try {
            return mapper.writeValueAsString(map);
        } catch (IOException e) {
            return "{}";
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> result = new LinkedHashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            int idx = pair.indexOf('=');
            if (idx < 0) {
                result.put(decode(pair), "");
            } else {
                result.put(decode(pair.substring(0, idx)),
                        decode(pair.substring(idx + 1)));
            }
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, Object body)
            throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
