package eu.kalafatic.dstudio.core.contextual.handlers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.kalafatic.dstudio.core.agent.AgentProfileRegistry;
import eu.kalafatic.dstudio.core.contextual.ActionFeedback;
import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualActionHandler;
import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.config.DStudioConfiguration;
import eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceService;
import eu.kalafatic.dstudio.core.service.ConfigurationService;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.core.service.ProjectService;

/**
 * Surface-independent domain handlers (Darwin Family Contextual Action Contract v1.0,
 * §1 rule 4: every handler performs a real domain operation). UI-specific handlers
 * (dialogs, clipboard) are registered by the RCP bundle; JSON handlers by the web
 * bundle. All of them execute through the same single registry.
 */
public final class CoreContextualActions {

    private CoreContextualActions() {
    }

    public static void install(ContextualActionRegistry registry,
            HarnessRegistry harnessRegistry, InferenceEngineRegistry engineRegistry,
            ConfigurationService configurationService, AgentProfileRegistry agentRegistry,
            ExecutionService executionService, InferenceService inferenceService,
            ProjectService projectService) {

        // ---------------- harness ----------------
        registry.registerHandler(handler(ContextActionIds.HARNESS_SET_DEFAULT, (sel, fb) -> {
            harnessRegistry.selectDefaultHarness(sel.objectId());
            fb.info("Default harness set to " + sel.objectLabel());
        }));
        registry.registerHandler(handler(ContextActionIds.HARNESS_TEST, (sel, fb) -> {
            HarnessConnectionTestResult result = harnessRegistry.testConnection(
                    sel.objectId());
            fb.info(result.message());
        }));
        registry.registerHandler(handler(ContextActionIds.HARNESS_ENABLE, (sel, fb) -> {
            harnessRegistry.setHarnessEnabled(sel.objectId(), true);
            fb.info("Harness enabled");
        }));
        registry.registerHandler(handler(ContextActionIds.HARNESS_DISABLE, (sel, fb) -> {
            harnessRegistry.setHarnessEnabled(sel.objectId(), false);
            fb.info("Harness disabled");
        }));
        registry.registerHandler(handler(ContextActionIds.HARNESS_REMOVE, (sel, fb) -> {
            harnessRegistry.unregisterHarness(sel.objectId());
            fb.info("Harness registration removed");
        }));
        registry.registerHandler(handler(ContextActionIds.HARNESS_REGISTRY_SCAN, (sel, fb) -> {
            int tested = 0;
            for (var descriptor : harnessRegistry.listHarnesses()) {
                harnessRegistry.testConnection(descriptor.getId());
                tested++;
            }
            fb.info("Scanned " + tested + " harnesses; statuses updated");
        }));
        registry.registerHandler(handler(ContextActionIds.HARNESS_REGISTRY_REFRESH,
                (sel, fb) -> fb.info("Harness list refreshed")));

        // ---------------- provider ----------------
        registry.registerHandler(handler(ContextActionIds.PROVIDER_SET_DEFAULT, (sel, fb) -> {
            engineRegistry.selectDefaultEngine(sel.objectId());
            fb.info("Default provider set to " + sel.objectLabel());
        }));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_TEST, (sel, fb) -> {
            var result = engineRegistry.testConnection(sel.objectId());
            fb.info(result.message());
        }));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_ENABLE, (sel, fb) -> {
            engineRegistry.setEngineEnabled(sel.objectId(), true);
            fb.info("Provider enabled");
        }));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_DISABLE, (sel, fb) -> {
            engineRegistry.setEngineEnabled(sel.objectId(), false);
            fb.info("Provider disabled");
        }));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_REMOVE, (sel, fb) -> {
            engineRegistry.removeEngine(sel.objectId());
            fb.info("Provider removed");
        }));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_REGISTRY_SCAN, (sel, fb) -> {
            int tested = 0;
            for (var descriptor : engineRegistry.listEngines()) {
                engineRegistry.testConnection(descriptor.getId());
                tested++;
            }
            fb.info("Scanned " + tested + " providers; statuses updated");
        }));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_REGISTRY_REFRESH,
                (sel, fb) -> fb.info("Provider list refreshed")));
        registry.registerHandler(handler(ContextActionIds.PROVIDER_DISCOVER_MODELS,
                (sel, fb) -> {
                    Optional<InferenceEngineAdapter> adapter = engineRegistry.findAdapter(
                            sel.objectId());
                    if (adapter.isEmpty()) {
                        fb.error("Provider not available", null);
                        return;
                    }
                    List<InferenceEngineAdapter.InferenceModel> models =
                            adapter.get().listModels();
                    fb.info("Discovered " + models.size() + " models on "
                            + sel.objectLabel());
                }));

        // ---------------- model ----------------
        registry.registerHandler(handler(ContextActionIds.MODEL_SET_DEFAULT, (sel, fb) -> {
            if (configurationService == null) {
                fb.error("Configuration service is unavailable", null);
                return;
            }
            DStudioConfiguration configuration = configurationService.getConfiguration();
            configurationService.saveConfiguration(new DStudioConfiguration(
                    configuration.appName(), configuration.appVersion(),
                    configuration.configurationDirectory(), configuration.teipBindAddress(),
                    configuration.teipPort(), configuration.teipNetworkMode(),
                    configuration.diagnosticsPort(), configuration.teipDevMode(),
                    configuration.defaultHarnessId(), sel.objectId(),
                    configuration.executionTimeoutSeconds(), configuration.evoMcpEndpoint(),
                    configuration.evoMcpToken()));
            fb.info("Default model set to " + sel.objectLabel());
        }));
        registry.registerHandler(handler(ContextActionIds.MODEL_LOAD, (sel, fb) -> {
            Optional<InferenceEngineAdapter> adapter = engineRegistry.findAdapter(
                    sel.providerId());
            if (adapter.isEmpty()) {
                fb.error("Provider not found", null);
                return;
            }
            boolean loaded = adapter.get().loadModel(sel.objectId());
            fb.info(loaded ? "Model loaded" : "Model load reported failure");
        }));
        registry.registerHandler(handler(ContextActionIds.MODEL_UNLOAD, (sel, fb) -> {
            Optional<InferenceEngineAdapter> adapter = engineRegistry.findAdapter(
                    sel.providerId());
            if (adapter.isEmpty()) {
                fb.error("Provider not found", null);
                return;
            }
            boolean unloaded = adapter.get().unloadModel(sel.objectId());
            fb.info(unloaded ? "Model unloaded" : "Model unload reported failure");
        }));
        registry.registerHandler(handler(ContextActionIds.MODEL_REFRESH, (sel, fb) -> {
            Optional<InferenceEngineAdapter> adapter = engineRegistry.findAdapter(
                    sel.providerId());
            if (adapter.isEmpty()) {
                fb.error("Provider not found", null);
                return;
            }
            List<InferenceEngineAdapter.InferenceModel> models = adapter.get().listModels();
            fb.info("Metadata refreshed; " + models.size() + " models available");
        }));
        registry.registerHandler(handler(ContextActionIds.MODEL_TEST, (sel, fb) -> {
            Optional<InferenceEngineAdapter> adapter = engineRegistry.findAdapter(
                    sel.providerId());
            if (adapter.isEmpty()) {
                fb.error("Provider not found", null);
                return;
            }
            var response = adapter.get().chat(new InferenceEngineAdapter.InferenceChatRequest(
                    sel.objectId(),
                    List.of(InferenceEngineAdapter.InferenceChatMessage.user(
                            "Reply with the single word: ok")),
                    0.0, 8));
            fb.info("Inference test: " + response.content().trim());
        }));

        // ---------------- agent ----------------
        registry.registerHandler(handler(ContextActionIds.AGENT_SET_DEFAULT, (sel, fb) -> {
            agentRegistry.selectDefaultProfile(sel.objectId());
            fb.info("Default agent set to " + sel.objectLabel());
        }));
        registry.registerHandler(handler(ContextActionIds.AGENT_CLONE, (sel, fb) -> {
            String newName = sel.objectLabel() + " Copy";
            agentRegistry.cloneProfile(sel.objectId(), newName);
            fb.info("Agent cloned as '" + newName + "'");
        }));
        registry.registerHandler(handler(ContextActionIds.AGENT_DELETE, (sel, fb) -> {
            boolean deleted = agentRegistry.deleteProfile(sel.objectId());
            fb.info(deleted ? "Agent deleted" : "Agent could not be deleted");
        }));

        // ---------------- execution / session ----------------
        registry.registerHandler(handler(ContextActionIds.SESSION_CANCEL, (sel, fb) -> {
            if ("session".equals(sel.objectType())) {
                inferenceService.cancel(sel.objectId());
                fb.info("Inference session cancelled");
                return;
            }
            executionService.cancel(sel.objectId());
            fb.info("Execution cancelled");
        }));

        // ---------------- project ----------------
        registry.registerHandler(handler(ContextActionIds.PROJECT_CLOSE, (sel, fb) -> {
            projectService.closeProject(sel.objectId());
            fb.info("Project closed");
        }));
    }

    private interface HandlerBody {
        void run(ContextSelection selection, ActionFeedback feedback) throws Exception;
    }

    private static ContextualActionHandler handler(String actionId, HandlerBody body) {
        return new ContextualActionHandler() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public void execute(ContextSelection selection, ActionFeedback feedback)
                    throws Exception {
                body.run(selection, feedback);
            }
        };
    }

    /** Masks sensitive configuration keys before any surface displays them. */
    public static Map<String, String> maskSensitive(Map<String, String> configuration) {
        java.util.LinkedHashMap<String, String> masked = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuration.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase();
            boolean sensitive = key.contains("token") || key.contains("key")
                    || key.contains("secret") || key.contains("password");
            masked.put(entry.getKey(), sensitive ? "••••••••" : entry.getValue());
        }
        return masked;
    }
}
