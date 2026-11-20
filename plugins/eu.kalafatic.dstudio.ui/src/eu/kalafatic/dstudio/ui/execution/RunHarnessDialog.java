package eu.kalafatic.dstudio.ui.execution;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import eu.kalafatic.dstudio.core.agent.AgentProfileRegistry;
import eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.core.service.ProjectService;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

public class RunHarnessDialog extends Dialog {

    private Combo projectCombo;
    private Combo harnessCombo;
    private Combo providerCombo;
    private Combo modelCombo;
    private Combo agentCombo;
    private Text instructionText;
    private Button autoApproveCheckbox;
    private List<Project> projects = List.of();
    private List<HarnessDescriptor> harnesses = List.of();
    private List<AgentProfile> agentProfiles = List.of();
    private List<String> engineIds = List.of();
    private static final String MODEL_LOADING_PLACEHOLDER = "(loading models…)";
    private static final String MODEL_DISCOVERY_FAILED_PREFIX =
            "(model discovery failed";
    /** Bumps on every refresh request so stale async results are discarded. */
    private int modelLoadGeneration;

    public RunHarnessDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Run Coding Task");
        shell.setSize(620, 520);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(2, false));
        ProjectService projectService = DStudioUiPlugin.getInstance().projectService();
        HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
        AgentProfileRegistry agentRegistry = DStudioUiPlugin.getInstance()
                .agentProfileRegistry();
        projects = projectService == null ? List.of() : projectService.listProjects();
        harnesses = registry == null ? List.of() : registry.listHarnesses();
        agentProfiles = agentRegistry == null ? List.of() : agentRegistry.listProfiles();
        var engineRegistry = DStudioUiPlugin.getInstance().inferenceEngineRegistry();
        engineIds = new ArrayList<>();
        if (engineRegistry != null) {
            for (var engine : engineRegistry.listEngines()) {
                if (engine.isEnabled()) {
                    engineIds.add(engine.getId());
                }
            }
        }
        Label projectLabel = new Label(container, SWT.NONE);
        projectLabel.setText("Project");
        projectCombo = new Combo(container, SWT.READ_ONLY);
        projectCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        for (Project project : projects) {
            projectCombo.add(project.name());
        }
        if (!projects.isEmpty()) {
            projectCombo.select(0);
        }
        Label harnessLabel = new Label(container, SWT.NONE);
        harnessLabel.setText("Harness");
        harnessCombo = new Combo(container, SWT.READ_ONLY);
        harnessCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        for (HarnessDescriptor harness : harnesses) {
            harnessCombo.add(harness.getId() + " (" + harness.getType() + ")");
        }
        if (registry != null) {
            String selected = registry.getSelectedDefaultHarnessId().orElse("");
            for (int i = 0; i < harnesses.size(); i++) {
                if (harnesses.get(i).getId().equals(selected)) {
                    harnessCombo.select(i);
                }
            }
        }
        if (harnessCombo.getSelectionIndex() < 0 && !harnesses.isEmpty()) {
            harnessCombo.select(0);
        }
        Label providerLabel = new Label(container, SWT.NONE);
        providerLabel.setText("Provider");
        providerCombo = new Combo(container, SWT.READ_ONLY);
        providerCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        providerCombo.add("");
        for (String engineId : engineIds) {
            providerCombo.add(engineId);
        }
        if (engineRegistry != null
                && engineRegistry.getSelectedDefaultEngineId().isPresent()) {
            String defaultEngine = engineRegistry.getSelectedDefaultEngineId().get();
            int index = engineIds.indexOf(defaultEngine) + 1;
            if (index > 0) {
                providerCombo.select(index);
            }
        }
        if (providerCombo.getSelectionIndex() < 0) {
            providerCombo.select(0);
        }
        Label modelLabel = new Label(container, SWT.NONE);
        modelLabel.setText("Model");
        modelCombo = new Combo(container, SWT.BORDER);
        modelCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        modelCombo.setToolTipText("Models from inference engines compatible with the "
                + "selected harness; any provider/model value can also be typed");
        refreshModelChoices();
        // models must follow the harness selection: only compatible engine types are offered
        harnessCombo.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent event) {
                refreshModelChoices();
            }
        });
        Label agentLabel = new Label(container, SWT.NONE);
        agentLabel.setText("Agent Profile");
        agentCombo = new Combo(container, SWT.READ_ONLY);
        agentCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        agentCombo.add("");
        for (AgentProfile profile : agentProfiles) {
            agentCombo.add(profile.getName());
        }
        if (agentRegistry != null && agentRegistry.getDefaultProfileId().isPresent()) {
            String defaultProfile = agentRegistry.getDefaultProfileId().get();
            for (int i = 0; i < agentProfiles.size(); i++) {
                if (agentProfiles.get(i).getId().equals(defaultProfile)) {
                    agentCombo.select(i + 1);
                }
            }
        }
        if (agentCombo.getSelectionIndex() < 0) {
            agentCombo.select(0);
        }
        Label instructionLabel = new Label(container, SWT.NONE);
        instructionLabel.setText("Instruction");
        instructionText = new Text(container, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL
                | SWT.WRAP);
        instructionText.setLayoutData(new GridData(GridData.FILL_BOTH));
        Label autoLabel = new Label(container, SWT.NONE);
        autoLabel.setText("Auto-approve");
        autoApproveCheckbox = new Button(container, SWT.CHECK);
        autoApproveCheckbox.setSelection(false);
        return container;
    }

    @Override
    protected void okPressed() {
        ExecutionService executionService = DStudioUiPlugin.getInstance().executionService();
        if (executionService == null || projects.isEmpty() || harnesses.isEmpty()) {
            super.okPressed();
            return;
        }
        Project project = projects.get(projectCombo.getSelectionIndex());
        HarnessDescriptor harness = harnesses.get(harnessCombo.getSelectionIndex());
        String providerId = "";
        if (providerCombo.getSelectionIndex() > 0) {
            int engineIndex = providerCombo.getSelectionIndex() - 1;
            if (engineIndex < engineIds.size()) {
                providerId = engineIds.get(engineIndex);
            }
        }
        String agentProfileId = "";
        if (agentCombo.getSelectionIndex() > 0) {
            int profileIndex = agentCombo.getSelectionIndex() - 1;
            if (profileIndex < agentProfiles.size()) {
                agentProfileId = agentProfiles.get(profileIndex).getId();
            }
        }
        String model = modelCombo.getText().trim();
        if (model.startsWith(MODEL_DISCOVERY_FAILED_PREFIX)) {
            // the error placeholder is informative only, never a model id
            model = "";
        }
        String instruction = instructionText.getText().trim();
        if (instruction.isBlank()) {
            org.eclipse.jface.dialogs.MessageDialog.openError(getShell(),
                    "Run Task", "Instruction is required.");
            return;
        }
        ExecutionService.ExecutionRequest request = new ExecutionService.ExecutionRequest(
                harness.getId(), project.id(), null, instruction, null,
                model.isBlank() ? null : model, null,
                autoApproveCheckbox.getSelection(), project.rootPath(),
                Duration.ofMinutes(30), providerId, agentProfileId,
                java.util.Map.of(), java.util.List.of());
        try {
            executionService.submit(request);
        } catch (RuntimeException e) {
            org.eclipse.jface.dialogs.MessageDialog.openError(getShell(), "Run Task",
                    "Submission failed: " + e.getMessage());
            return;
        }
        super.okPressed();
    }

    /**
     * Populates the model combo from the registered inference engines, filtered by the
     * harness's supported provider types (compatibility matrix) so the user is never
     * offered an incompatible combination. Free text remains available for explicit
     * provider/model values.
     *
     * <p>Model discovery is a blocking network operation (up to ~120s per engine), so it
     * runs on a background thread; the combo shows a placeholder while loading and an
     * explicit error entry when discovery fails. Stale results (a newer refresh request
     * arrived, or the dialog closed) are discarded.</p>
     */
    private void refreshModelChoices() {
        final String current = modelCombo.getText();
        final int request = ++modelLoadGeneration;
        modelCombo.removeAll();
        modelCombo.add(MODEL_LOADING_PLACEHOLDER);
        modelCombo.setText("");
        HarnessDescriptor harness = selectedHarness();
        var engineRegistry = DStudioUiPlugin.getInstance().inferenceEngineRegistry();
        if (engineRegistry == null || harness == null) {
            modelCombo.removeAll();
            modelCombo.add("");
            modelCombo.select(0);
            return;
        }
        // Snapshot the compatibility filter and the engine descriptors on the UI thread;
        // model discovery itself runs in the background.
        java.util.Set<String> supported = new java.util.LinkedHashSet<>(
                harnessRegistryCompatibility(harness));
        List<eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor> engines =
                new ArrayList<>();
        for (var engine : engineRegistry.listEngines()) {
            engines.add(engine);
        }
        org.eclipse.swt.widgets.Display display = getShell().getDisplay();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            java.util.Set<String> added = new java.util.LinkedHashSet<>();
            List<String> discovered = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            for (var engine : engines) {
                if (!engine.isEnabled() || (engine.getStatus() != null
                        && engine.getStatus().name().equals("ERROR"))) {
                    continue;
                }
                if (!supported.isEmpty() && !supported.contains(engine.getType())) {
                    continue;
                }
                var adapter = engineRegistry.findAdapter(engine.getId());
                if (adapter.isEmpty()) {
                    continue;
                }
                try {
                    for (var model : adapter.get().listModels()) {
                        if (added.add(model.id())) {
                            discovered.add(model.id());
                        }
                    }
                } catch (RuntimeException discoveryFailed) {
                    failures.add(engine.getId() + ": " + discoveryFailed.getMessage());
                }
                String defaultModel = adapter.get().getConnectionConfiguration()
                        .getOrDefault("defaultModel", "");
                if (!defaultModel.isBlank() && added.add(defaultModel)) {
                    discovered.add(defaultModel);
                }
            }
            final java.util.Set<String> discoveredIds = added;
            final List<String> discoveryFailures = failures;
            display.asyncExec(() -> {
                if (request != modelLoadGeneration || modelCombo == null
                        || modelCombo.isDisposed()) {
                    return;
                }
                modelCombo.removeAll();
                modelCombo.add("");
                for (String model : discovered) {
                    modelCombo.add(model);
                }
                if (!discoveryFailures.isEmpty()) {
                    modelCombo.add(MODEL_DISCOVERY_FAILED_PREFIX + ": "
                            + String.join("; ", discoveryFailures) + ")");
                }
                if (discoveredIds.contains(current)) {
                    modelCombo.setText(current);
                } else if (modelCombo.getItemCount() > 0) {
                    modelCombo.select(0);
                }
            });
        });
    }

    private java.util.Set<String> harnessRegistryCompatibility(HarnessDescriptor harness) {
        var registry = DStudioUiPlugin.getInstance().harnessRegistry();
        if (registry == null) {
            return java.util.Set.of();
        }
        return registry.aggregateCapabilities(harness.getId()).stream()
                .map(HarnessCapabilityDescriptor::supportedProviderTypes)
                .filter(java.util.Objects::nonNull)
                .flatMap(java.util.List::stream)
                .collect(java.util.stream.Collectors.toSet());
    }

    private HarnessDescriptor selectedHarness() {
        int index = harnessCombo.getSelectionIndex();
        return index >= 0 && index < harnesses.size() ? harnesses.get(index) : null;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Run", true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL,
                false);
    }
}
