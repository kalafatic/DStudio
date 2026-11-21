package eu.kalafatic.dstudio.ui.contextual;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import eu.kalafatic.dstudio.core.agent.AgentProfileRegistry;
import eu.kalafatic.dstudio.core.contextual.ActionFeedback;
import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualActionHandler;
import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineFactory;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import eu.kalafatic.dstudio.ui.agent.AgentConfigDialog;
import eu.kalafatic.dstudio.ui.execution.RunHarnessDialog;
import eu.kalafatic.dstudio.ui.harness.HarnessConfigDialog;

/**
 * RCP-surface handlers for the unified contextual action framework: dialogs, clipboard
 * and explorer interaction. Every handler performs a real operation; dialog edits are
 * written back through the domain registries, exactly like the existing toolbar buttons.
 */
public final class RcpContextualActions {

    private RcpContextualActions() {
    }

    public static void install(ContextualActionRegistry registry) {
        DStudioUiPlugin plugin = DStudioUiPlugin.getInstance();

        // ---------------- harness ----------------
        registry.registerHandler(rcp(ContextActionIds.HARNESS_EDIT, (sel, fb) -> {
            HarnessRegistry harnessRegistry = plugin.harnessRegistry();
            Optional<HarnessDescriptor> descriptor = harnessRegistry == null
                    ? Optional.empty() : harnessRegistry.findHarness(sel.objectId());
            if (descriptor.isEmpty()) {
                fb.error("Harness not found", null);
                return;
            }
            openDialog(shell(), "Edit Harness Configuration", () -> {
                HarnessConfigDialog dialog = new HarnessConfigDialog(shell(),
                        descriptor.get(), harnessRegistry);
                if (dialog.open() == Window.OK) {
                    harnessRegistry.updateConfiguration(sel.objectId(),
                            dialog.getConfiguration());
                    fb.info("Configuration updated");
                }
            });
        }));
        registry.registerHandler(rcp(ContextActionIds.HARNESS_VIEW_CAPABILITIES,
                (sel, fb) -> {
                    HarnessRegistry harnessRegistry = plugin.harnessRegistry();
                    List<HarnessCapabilityDescriptor> capabilities =
                            harnessRegistry == null ? List.of()
                                    : harnessRegistry.aggregateCapabilities(sel.objectId());
                    StringBuilder text = new StringBuilder();
                    text.append("Harness: ").append(sel.objectLabel()).append("\n");
                    if (!capabilities.isEmpty()) {
                        for (HarnessCapabilityDescriptor caps : capabilities) {
                            text.append("Modes: ").append(caps.supportedModes())
                                    .append("\n");
                            text.append("Features: ").append(caps.features()).append("\n");
                            text.append("Supported provider types: ")
                                    .append(caps.supportedProviderTypes()).append("\n");
                            text.append("Agents: ").append(caps.agents()).append("\n");
                            text.append("Models: ").append(caps.models()).append("\n");
                        }
                    } else {
                        text.append("No capability descriptor published.\n");
                    }
                    showInfo("Harness Capabilities", text.toString());
                }));

        // ---------------- provider ----------------
        registry.registerHandler(rcp(ContextActionIds.PROVIDER_EDIT, (sel, fb) -> {
            InferenceEngineRegistry engineRegistry = plugin.inferenceEngineRegistry();
            if (engineRegistry == null || engineRegistry.findEngine(sel.objectId())
                    .isEmpty()) {
                fb.error("Provider not found", null);
                return;
            }
            openDialog(shell(), "Edit Provider Configuration", () -> {
                InferenceEngineConfigDialog dialog = new InferenceEngineConfigDialog(
                        shell(), sel.objectLabel(), engineRegistry.getConfiguration(
                                sel.objectId()));
                if (dialog.open() == Window.OK) {
                    engineRegistry.updateConfiguration(sel.objectId(),
                            dialog.getConfiguration());
                    fb.info("Configuration updated");
                }
            });
        }));
        registry.registerHandler(rcp(ContextActionIds.PROVIDER_VIEW_ENDPOINT, (sel, fb) -> {
            InferenceEngineRegistry engineRegistry = plugin.inferenceEngineRegistry();
            Map<String, String> configuration = engineRegistry == null ? Map.of()
                    : engineRegistry.getConfiguration(sel.objectId());
            Map<String, String> masked = eu.kalafatic.dstudio.core.contextual.handlers
                    .CoreContextualActions.maskSensitive(configuration);
            StringBuilder text = new StringBuilder();
            text.append("Provider: ").append(sel.objectLabel()).append("\n");
            for (Map.Entry<String, String> entry : masked.entrySet()) {
                text.append("  ").append(entry.getKey()).append(" = ")
                        .append(entry.getValue()).append("\n");
            }
            showInfo("Provider Endpoint", text.toString());
        }));
        registry.registerHandler(rcp(ContextActionIds.PROVIDER_REGISTRY_ADD, (sel, fb) -> {
            InferenceEngineFactory factory = plugin.coordinator() == null ? null
                    : plugin.coordinator().getInferenceEngineFactory();
            InferenceEngineRegistry engineRegistry = plugin.inferenceEngineRegistry();
            if (factory == null || engineRegistry == null) {
                fb.error("Provider registration is not available in this runtime", null);
                return;
            }
            openDialog(shell(), "Add Provider", () -> {
                AddProviderDialog dialog = new AddProviderDialog(shell());
                if (dialog.open() == Window.OK) {
                    Optional<InferenceEngineAdapter> engine = factory.createEngine(
                            dialog.getType(), dialog.getId(), dialog.getName(),
                            dialog.getConfiguration());
                    if (engine.isEmpty()) {
                        showError("Add Provider", "Unknown provider type: "
                                + dialog.getType());
                        return;
                    }
                    engineRegistry.registerEngine(engine.get());
                    fb.info("Provider '" + dialog.getId() + "' registered");
                }
            });
        }));

        // ---------------- model ----------------
        registry.registerHandler(rcp(ContextActionIds.MODEL_INFO, (sel, fb) -> {
            InferenceEngineRegistry engineRegistry = plugin.inferenceEngineRegistry();
            String engineId = sel.providerId();
            Optional<InferenceEngineAdapter> adapter = engineRegistry == null
                    ? Optional.empty() : engineRegistry.findAdapter(engineId);
            String provider = adapter.map(a -> a.getEngineName())
                    .orElse(engineId.isBlank() ? "unknown" : engineId);
            showInfo("Model Information", "Model: " + sel.objectLabel()
                    + "\nProvider: " + provider
                    + "\nState: " + sel.objectState());
        }));
        registry.registerHandler(rcp(ContextActionIds.MODEL_COPY_INFO, (sel, fb) -> {
            Clipboard clipboard = new Clipboard(shell().getDisplay());
            clipboard.setContents(new Object[] { sel.objectId() },
                    new Transfer[] { TextTransfer.getInstance() });
            clipboard.dispose();
            fb.info("Model id copied to clipboard");
        }));

        // ---------------- agent ----------------
        registry.registerHandler(rcp(ContextActionIds.AGENT_REGISTRY_CREATE, (sel, fb) -> {
            AgentProfileRegistry agentRegistry = plugin.agentProfileRegistry();
            if (agentRegistry == null) {
                fb.error("Agent registry unavailable", null);
                return;
            }
            openDialog(shell(), "Create Agent", () -> {
                AgentConfigDialog dialog = new AgentConfigDialog(shell(), null);
                if (dialog.open() == Window.OK) {
                    agentRegistry.createProfile(dialog.getProfile());
                    fb.info("Agent created");
                }
            });
        }));
        registry.registerHandler(rcp(ContextActionIds.AGENT_EDIT, (sel, fb) -> {
            AgentProfileRegistry agentRegistry = plugin.agentProfileRegistry();
            Optional<AgentProfile> profile = agentRegistry == null ? Optional.empty()
                    : agentRegistry.findProfile(sel.objectId());
            if (profile.isEmpty()) {
                fb.error("Agent not found", null);
                return;
            }
            openDialog(shell(), "Edit Agent", () -> {
                AgentConfigDialog dialog = new AgentConfigDialog(shell(), profile.get());
                if (dialog.open() == Window.OK) {
                    agentRegistry.updateProfile(dialog.getProfile());
                    fb.info("Agent updated");
                }
            });
        }));
        for (String id : new String[] { ContextActionIds.AGENT_CONFIGURE_INSTRUCTIONS,
                ContextActionIds.AGENT_CONFIGURE_TOOLS,
                ContextActionIds.AGENT_CONFIGURE_PERMISSIONS }) {
            registry.registerHandler(rcp(id, (sel, fb) -> {
                AgentProfileRegistry agentRegistry = plugin.agentProfileRegistry();
                Optional<AgentProfile> profile = agentRegistry == null ? Optional.empty()
                        : agentRegistry.findProfile(sel.objectId());
                if (profile.isEmpty()) {
                    fb.error("Agent not found", null);
                    return;
                }
                openDialog(shell(), "Configure Agent", () -> {
                    AgentConfigDialog dialog = new AgentConfigDialog(shell(),
                            profile.get());
                    if (dialog.open() == Window.OK) {
                        agentRegistry.updateProfile(dialog.getProfile());
                        fb.info("Agent configuration updated");
                    }
                });
            }));
        }
        registry.registerHandler(rcp(ContextActionIds.AGENT_EXPORT, (sel, fb) -> {
            AgentProfileRegistry agentRegistry = plugin.agentProfileRegistry();
            Optional<AgentProfile> profile = agentRegistry == null ? Optional.empty()
                    : agentRegistry.findProfile(sel.objectId());
            if (profile.isEmpty()) {
                fb.error("Agent not found", null);
                return;
            }
            FileDialog dialog = new FileDialog(shell(), SWT.SAVE);
            dialog.setFileName(sel.objectId() + ".agent.json");
            dialog.setFilterExtensions(new String[] { "*.json" });
            String path = dialog.open();
            if (path == null) {
                return;
            }
            try {
                Files.writeString(Path.of(path),
                        AgentProfileJson.toJson(profile.get()),
                        StandardCharsets.UTF_8);
                fb.info("Agent exported to " + path);
            } catch (IOException e) {
                fb.error("Export failed: " + e.getMessage(), e);
            }
        }));
        registry.registerHandler(rcp(ContextActionIds.AGENT_REGISTRY_IMPORT, (sel, fb) -> {
            AgentProfileRegistry agentRegistry = plugin.agentProfileRegistry();
            if (agentRegistry == null) {
                fb.error("Agent registry unavailable", null);
                return;
            }
            FileDialog dialog = new FileDialog(shell(), SWT.OPEN);
            dialog.setFilterExtensions(new String[] { "*.json" });
            String path = dialog.open();
            if (path == null) {
                return;
            }
            try {
                AgentProfile profile = AgentProfileJson.fromJson(
                        Files.readString(Path.of(path), StandardCharsets.UTF_8));
                agentRegistry.createProfile(profile);
                fb.info("Agent imported");
            } catch (Exception e) {
                fb.error("Import failed: " + e.getMessage(), e);
            }
        }));
        registry.registerHandler(rcp(ContextActionIds.AGENT_COMPATIBILITY, (sel, fb) -> {
            AgentProfileRegistry agentRegistry = plugin.agentProfileRegistry();
            if (agentRegistry == null) {
                fb.error("Agent registry unavailable", null);
                return;
            }
            String harnessId = sel.harnessId();
            String providerId = sel.providerId();
            if (harnessId.isBlank()) {
                harnessId = plugin.harnessRegistry() == null ? ""
                        : plugin.harnessRegistry().getSelectedDefaultHarnessId()
                                .orElse("");
            }
            if (providerId.isBlank()) {
                providerId = plugin.inferenceEngineRegistry() == null ? ""
                        : plugin.inferenceEngineRegistry().getSelectedDefaultEngineId()
                                .orElse("");
            }
            var result = agentRegistry.checkCompatibility(sel.objectId(), harnessId,
                    providerId, sel.modelId());
            showInfo("Agent Compatibility", result.compatible()
                    ? "Compatible with the selected environment." : result.message());
        }));

        // ---------------- execution / session ----------------
        registry.registerHandler(rcp(ContextActionIds.SESSION_DETAILS, (sel, fb) -> {
            ExecutionService executionService = plugin.executionService();
            Optional<ExecutionTask> task = executionService == null ? Optional.empty()
                    : executionService.getTask(sel.objectId());
            if (task.isEmpty()) {
                fb.error("Execution not found", null);
                return;
            }
            ExecutionTask t = task.get();
            showInfo("Execution Details", "Id: " + t.getId()
                    + "\nStatus: " + t.getStatus()
                    + "\nHarness: " + t.getHarnessId()
                    + "\nProject: " + t.getProjectId()
                    + "\nModel: " + t.getModel()
                    + "\nAgent: " + t.getAgent()
                    + "\nInstruction: " + t.getInstruction()
                    + (t.getErrorMessage() == null || t.getErrorMessage().isBlank()
                            ? "" : "\nError: " + t.getErrorMessage()));
        }));
        registry.registerHandler(rcp(ContextActionIds.SESSION_HISTORY, (sel, fb) -> {
            ExecutionService executionService = plugin.executionService();
            List<eu.kalafatic.dstudio.core.model.ExecutionEvent> events =
                    executionService == null ? List.of()
                            : executionService.getEvents(sel.objectId(), 0, 50);
            StringBuilder text = new StringBuilder("Events (" + events.size()
                    + " shown):\n");
            for (eu.kalafatic.dstudio.core.model.ExecutionEvent event : events) {
                text.append("  ").append(event.type()).append(" @ ")
                        .append(event.timestamp()).append(" — ")
                        .append(event.message() == null ? "" : event.message())
                        .append("\n");
            }
            showInfo("Execution History", text.toString());
        }));

        // ---------------- project ----------------
        // RCP close also removes the workspace bridge project, preserving the previous
        // explorer behavior; the web surface keeps the pure domain close.
        registry.registerHandler(rcp(ContextActionIds.PROJECT_CLOSE, (sel, fb) -> {
            var projectService = plugin.projectService();
            Optional<eu.kalafatic.dstudio.core.model.Project> project =
                    projectService == null ? Optional.empty()
                            : projectService.findProject(sel.objectId());
            if (project.isEmpty()) {
                fb.error("Project not found", null);
                return;
            }
            projectService.closeProject(sel.objectId());
            new eu.kalafatic.dstudio.ui.workspace.EclipseWorkspaceBridge()
                    .removeProject(project.get());
            fb.info("Project closed");
        }));
        registry.registerHandler(rcp(ContextActionIds.PROJECT_OPEN, (sel, fb) -> {
            var projectService = plugin.projectService();
            Optional<eu.kalafatic.dstudio.core.model.Project> project =
                    projectService == null ? Optional.empty()
                            : projectService.findProject(sel.objectId());
            if (project.isEmpty()) {
                fb.error("Project not found", null);
                return;
            }
            try {
                new eu.kalafatic.dstudio.ui.workspace.EclipseWorkspaceBridge()
                        .ensureProject(project.get());
                fb.info("Project opened in the workspace");
            } catch (Exception e) {
                fb.error("Could not open project in workspace: " + e.getMessage(), e);
            }
        }));
        registry.registerHandler(rcp(ContextActionIds.PROJECT_CREATE_SESSION, (sel, fb) -> {
            openDialog(shell(), "Create Session", () -> {
                RunHarnessDialog dialog = new RunHarnessDialog(shell());
                if (dialog.open() == Window.OK) {
                    fb.info("Execution submitted");
                }
            });
        }));
        // Family surface actions: shared ids, executed through the shared launchers
        // (single implementation with the Tools menu entries).
        registry.registerHandler(rcp(ContextActionIds.TERMINAL_OPEN, (sel, fb) -> {
            String rootPath = sel.state("rootPath");
            if (rootPath.isBlank()) {
                var projectService = plugin.projectService();
                Optional<eu.kalafatic.dstudio.core.model.Project> project =
                        projectService == null ? Optional.empty()
                                : projectService.findProject(sel.objectId());
                rootPath = project
                        .map(eu.kalafatic.dstudio.core.model.Project::rootPath)
                        .orElse("");
            }
            if (rootPath.isBlank()) {
                fb.error("Project root path unavailable", null);
                return;
            }
            eu.kalafatic.dstudio.ui.workbench.TerminalLauncher.open(rootPath);
            fb.info("Terminal opened in " + rootPath);
        }));
        registry.registerHandler(rcp(ContextActionIds.WEB_OPEN, (sel, fb) -> {
            eu.kalafatic.dstudio.ui.workbench.WebInterfaceLauncher.openWebInterface();
            fb.info("Web interface opened");
        }));
    }

    // ---------------------------------------------------------------- plumbing

    private static ContextualActionHandler rcp(String actionId, Body body) {
        return new ContextualActionHandler() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String surface() {
                return ContextSelection.SURFACE_RCP;
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

    private static Shell shell() {
        return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    }

    private static void openDialog(Shell parent, String title, Runnable content) {
        Display display = parent.getDisplay();
        display.syncExec(content);
    }

    private static void showInfo(String title, String message) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> MessageDialog.openInformation(shell(), title, message));
    }

    private static void showError(String title, String message) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> MessageDialog.openError(shell(), title, message));
    }
}
