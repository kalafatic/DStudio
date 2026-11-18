package eu.kalafatic.dstudio.ui.agent;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import eu.kalafatic.dstudio.core.agent.AgentProfileRegistry;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;

/**
 * Agent Manager view (unified multi-agent dimension): create, edit, clone, delete, export
 * and default-select reusable agent profiles. All operations use the shared
 * {@link AgentProfileRegistry} backend — no UI-local business logic.
 */
public class AgentView extends ViewPart {

    public static final String VIEW_ID = "eu.kalafatic.dstudio.ui.view.agents";

    private TableViewer viewer;
    private Button createButton;
    private Button editButton;
    private Button cloneButton;
    private Button deleteButton;
    private Button defaultButton;
    private Button exportButton;

    @Override
    public void createPartControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(6).equalWidth(true).applyTo(container);
        viewer = new TableViewer(container, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).span(6, 1).applyTo(viewer.getTable());
        TableViewerColumn nameColumn = new TableViewerColumn(viewer, SWT.NONE);
        nameColumn.getColumn().setText("Agent Profile");
        nameColumn.getColumn().setWidth(150);
        nameColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((AgentProfile) element).getName();
            }
        });
        TableViewerColumn roleColumn = new TableViewerColumn(viewer, SWT.NONE);
        roleColumn.getColumn().setText("Role");
        roleColumn.getColumn().setWidth(120);
        roleColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((AgentProfile) element).getRole();
            }
        });
        TableViewerColumn harnessColumn = new TableViewerColumn(viewer, SWT.NONE);
        harnessColumn.getColumn().setText("Pref. Harness");
        harnessColumn.getColumn().setWidth(110);
        harnessColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((AgentProfile) element).getPreferredHarnessId();
            }
        });
        TableViewerColumn providerColumn = new TableViewerColumn(viewer, SWT.NONE);
        providerColumn.getColumn().setText("Pref. Provider");
        providerColumn.getColumn().setWidth(110);
        providerColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((AgentProfile) element).getPreferredProviderId();
            }
        });
        TableViewerColumn modelColumn = new TableViewerColumn(viewer, SWT.NONE);
        modelColumn.getColumn().setText("Pref. Model");
        modelColumn.getColumn().setWidth(130);
        modelColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((AgentProfile) element).getPreferredModel();
            }
        });
        TableViewerColumn defaultColumn = new TableViewerColumn(viewer, SWT.NONE);
        defaultColumn.getColumn().setText("Default");
        defaultColumn.getColumn().setWidth(80);
        defaultColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                AgentProfile profile = (AgentProfile) element;
                AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
                return registry != null && registry.getDefaultProfileId()
                        .map(profile.getId()::equals).orElse(false) ? "selected" : "";
            }
        });
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        createButton = new Button(container, SWT.PUSH);
        createButton.setText("Create…");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(createButton);
        editButton = new Button(container, SWT.PUSH);
        editButton.setText("Edit…");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(editButton);
        cloneButton = new Button(container, SWT.PUSH);
        cloneButton.setText("Clone");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(cloneButton);
        deleteButton = new Button(container, SWT.PUSH);
        deleteButton.setText("Delete");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(deleteButton);
        defaultButton = new Button(container, SWT.PUSH);
        defaultButton.setText("Set Default");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(defaultButton);
        exportButton = new Button(container, SWT.PUSH);
        exportButton.setText("Export…");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(exportButton);
        createButton.addListener(SWT.Selection, e -> onCreate());
        editButton.addListener(SWT.Selection, e -> onEdit());
        cloneButton.addListener(SWT.Selection, e -> onClone());
        deleteButton.addListener(SWT.Selection, e -> onDelete());
        defaultButton.addListener(SWT.Selection, e -> onSetDefault());
        exportButton.addListener(SWT.Selection, e -> onExport());

        // Unified contextual actions: agent menu on rows, registry menu on empty area.
        eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry contextualRegistry =
                DStudioUiPlugin.getInstance().contextualActionRegistry();
        if (contextualRegistry != null) {
            eu.kalafatic.dstudio.ui.contextual.ContextualMenuSupport.attach(
                    viewer.getTable(), contextualRegistry, () -> {
                        AgentProfile profile = selectedProfile();
                        AgentProfileRegistry registry =
                                DStudioUiPlugin.getInstance().agentProfileRegistry();
                        if (profile == null) {
                            return eu.kalafatic.dstudio.core.contextual.ContextSelection
                                    .builder("DSTUDIO", "RCP", "agent-registry").build();
                        }
                        boolean isDefault = registry != null
                                && registry.getDefaultProfileId()
                                        .map(profile.getId()::equals).orElse(false);
                        String harnessId = DStudioUiPlugin.getInstance()
                                .harnessRegistry() == null ? ""
                                        : DStudioUiPlugin.getInstance()
                                                .harnessRegistry()
                                                .getSelectedDefaultHarnessId().orElse("");
                        String providerId = DStudioUiPlugin.getInstance()
                                .inferenceEngineRegistry() == null ? ""
                                        : DStudioUiPlugin.getInstance()
                                                .inferenceEngineRegistry()
                                                .getSelectedDefaultEngineId().orElse("");
                        String modelId = DStudioUiPlugin.getInstance().coordinator() == null
                                ? ""
                                : DStudioUiPlugin.getInstance().coordinator()
                                        .getConfiguration().defaultModel();
                        return eu.kalafatic.dstudio.core.contextual.ContextSelection
                                .builder("DSTUDIO", "RCP", "agent")
                                .object(profile.getId(), profile.getName())
                                .state("isDefault", isDefault)
                                .harnessId(harnessId)
                                .providerId(providerId)
                                .modelId(modelId)
                                .build();
                    }, this::refresh);
        }
        refresh();
    }

    private AgentProfile selectedProfile() {
        IStructuredSelection selection = viewer.getStructuredSelection();
        return selection.isEmpty() ? null : (AgentProfile) selection.getFirstElement();
    }

    private void onCreate() {
        AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
        if (registry == null) {
            return;
        }
        AgentConfigDialog dialog = new AgentConfigDialog(getSite().getShell(), null);
        if (dialog.open() == Window.OK) {
            try {
                registry.createProfile(dialog.getProfile());
                refresh();
            } catch (RuntimeException e) {
                showError("Create agent profile", e.getMessage());
            }
        }
    }

    private void onEdit() {
        AgentProfile profile = selectedProfile();
        AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
        if (profile == null || registry == null) {
            return;
        }
        AgentConfigDialog dialog = new AgentConfigDialog(getSite().getShell(), profile);
        if (dialog.open() == Window.OK) {
            try {
                registry.updateProfile(dialog.getProfile());
                refresh();
            } catch (RuntimeException e) {
                showError("Edit agent profile", e.getMessage());
            }
        }
    }

    private void onClone() {
        AgentProfile profile = selectedProfile();
        AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
        if (profile == null || registry == null) {
            return;
        }
        try {
            registry.cloneProfile(profile.getId(), null);
            refresh();
        } catch (RuntimeException e) {
            showError("Clone agent profile", e.getMessage());
        }
    }

    private void onDelete() {
        AgentProfile profile = selectedProfile();
        AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
        if (profile == null || registry == null) {
            return;
        }
        boolean confirmed = org.eclipse.jface.dialogs.MessageDialog.openConfirm(
                getSite().getShell(), "Delete Agent Profile",
                "Delete agent profile '" + profile.getName() + "'?");
        if (confirmed) {
            registry.deleteProfile(profile.getId());
            refresh();
        }
    }

    private void onSetDefault() {
        AgentProfile profile = selectedProfile();
        AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
        if (profile != null && registry != null) {
            registry.selectDefaultProfile(profile.getId());
            refresh();
        }
    }

    private void onExport() {
        AgentProfile profile = selectedProfile();
        if (profile == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("name=").append(profile.getName()).append('\n');
        sb.append("description=").append(profile.getDescription()).append('\n');
        sb.append("role=").append(profile.getRole()).append('\n');
        sb.append("systemInstructions=").append(profile.getSystemInstructions()).append('\n');
        sb.append("toolPermissions=").append(String.join(",", profile.getToolPermissions()))
                .append('\n');
        sb.append("restrictions=").append(String.join(",", profile.getRestrictions()))
                .append('\n');
        sb.append("preferredHarness=").append(profile.getPreferredHarnessId()).append('\n');
        sb.append("preferredProvider=").append(profile.getPreferredProviderId()).append('\n');
        sb.append("preferredModel=").append(profile.getPreferredModel()).append('\n');
        org.eclipse.jface.dialogs.MessageDialog.openInformation(getSite().getShell(),
                "Agent Profile Export — " + profile.getName(), sb.toString());
    }

    private void showError(String title, String message) {
        org.eclipse.jface.dialogs.MessageDialog.openError(getSite().getShell(), title,
                message == null ? "Operation failed" : message);
    }

    public void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        AgentProfileRegistry registry = DStudioUiPlugin.getInstance().agentProfileRegistry();
        List<AgentProfile> profiles = registry == null ? List.of()
                : new ArrayList<>(registry.listProfiles());
        viewer.setInput(profiles);
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }
}
