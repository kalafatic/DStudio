package eu.kalafatic.dstudio.ui.harness;

import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.action.Action;
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

public class HarnessView extends ViewPart {

    public static final String VIEW_ID = "eu.kalafatic.dstudio.ui.view.harness";

    private TableViewer viewer;
    private Button selectButton;
    private Button enableButton;
    private Button testButton;
    private Button configButton;

    @Override
    public void createPartControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(4).equalWidth(true).applyTo(container);
        viewer = new TableViewer(container, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).span(4, 1).applyTo(viewer.getTable());
        TableViewerColumn idColumn = new TableViewerColumn(viewer, SWT.NONE);
        idColumn.getColumn().setText("Harness");
        idColumn.getColumn().setWidth(140);
        idColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                HarnessDescriptor descriptor = (HarnessDescriptor) element;
                return descriptor.getName();
            }
        });
        TableViewerColumn typeColumn = new TableViewerColumn(viewer, SWT.NONE);
        typeColumn.getColumn().setText("Type");
        typeColumn.getColumn().setWidth(90);
        typeColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((HarnessDescriptor) element).getType();
            }
        });
        TableViewerColumn enabledColumn = new TableViewerColumn(viewer, SWT.NONE);
        enabledColumn.getColumn().setText("Enabled");
        enabledColumn.getColumn().setWidth(70);
        enabledColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((HarnessDescriptor) element).isEnabled() ? "yes" : "no";
            }
        });
        TableViewerColumn statusColumn = new TableViewerColumn(viewer, SWT.NONE);
        statusColumn.getColumn().setText("Status");
        statusColumn.getColumn().setWidth(90);
        statusColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((HarnessDescriptor) element).getStatus().name();
            }
        });
        TableViewerColumn selectedColumn = new TableViewerColumn(viewer, SWT.NONE);
        selectedColumn.getColumn().setText("Default");
        selectedColumn.getColumn().setWidth(90);
        selectedColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                HarnessDescriptor descriptor = (HarnessDescriptor) element;
                HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
                return registry != null && registry.getSelectedDefaultHarnessId()
                        .map(descriptor.getId()::equals).orElse(false) ? "selected" : "";
            }
        });
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        selectButton = new Button(container, SWT.PUSH);
        selectButton.setText("Set Default");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(selectButton);
        enableButton = new Button(container, SWT.PUSH);
        enableButton.setText("Enable/Disable");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(enableButton);
        testButton = new Button(container, SWT.PUSH);
        testButton.setText("Test Connection");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(testButton);
        configButton = new Button(container, SWT.PUSH);
        configButton.setText("Configure…");
        GridDataFactory.fillDefaults().grab(true, false).applyTo(configButton);
        selectButton.addListener(SWT.Selection, e -> onSelectDefault());
        enableButton.addListener(SWT.Selection, e -> onToggleEnabled());
        testButton.addListener(SWT.Selection, e -> onTestConnection());
        configButton.addListener(SWT.Selection, e -> onConfigure());

        // Unified contextual actions: harness menu on rows, registry menu on empty area.
        eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry contextualRegistry =
                DStudioUiPlugin.getInstance().contextualActionRegistry();
        if (contextualRegistry != null) {
            eu.kalafatic.dstudio.ui.contextual.ContextualMenuSupport.attach(
                    viewer.getTable(), contextualRegistry, () -> {
                        HarnessDescriptor descriptor = selectedDescriptor();
                        HarnessRegistry registry =
                                DStudioUiPlugin.getInstance().harnessRegistry();
                        if (descriptor == null) {
                            return eu.kalafatic.dstudio.core.contextual.ContextSelection
                                    .builder("DSTUDIO", "RCP", "harness-registry").build();
                        }
                        boolean isDefault = registry != null
                                && registry.getSelectedDefaultHarnessId()
                                        .map(descriptor.getId()::equals).orElse(false);
                        return eu.kalafatic.dstudio.core.contextual.ContextSelection
                                .builder("DSTUDIO", "RCP", "harness")
                                .object(descriptor.getId(), descriptor.getName())
                                .state("enabled", descriptor.isEnabled())
                                .state("isDefault", isDefault)
                                .capabilities(descriptor.getCapabilities())
                                .build();
                    }, this::refresh);
        }
        refresh();
    }

    private HarnessDescriptor selectedDescriptor() {
        IStructuredSelection selection = viewer.getStructuredSelection();
        return selection.isEmpty() ? null : (HarnessDescriptor) selection.getFirstElement();
    }

    private void onSelectDefault() {
        HarnessDescriptor descriptor = selectedDescriptor();
        HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
        if (descriptor != null && registry != null) {
            registry.selectDefaultHarness(descriptor.getId());
            refresh();
        }
    }

    private void onToggleEnabled() {
        HarnessDescriptor descriptor = selectedDescriptor();
        HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
        if (descriptor != null && registry != null) {
            registry.setHarnessEnabled(descriptor.getId(), !descriptor.isEnabled());
            refresh();
        }
    }

    private void onTestConnection() {
        HarnessDescriptor descriptor = selectedDescriptor();
        HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
        if (descriptor != null && registry != null) {
            var result = registry.testConnection(descriptor.getId());
            org.eclipse.jface.dialogs.MessageDialog.openInformation(getSite().getShell(),
                    "Connection Test",
                    result.success() ? result.message() : result.message());
            refresh();
        }
    }

    private void onConfigure() {
        HarnessDescriptor descriptor = selectedDescriptor();
        HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
        if (descriptor == null || registry == null) {
            return;
        }
        HarnessConfigDialog dialog = new HarnessConfigDialog(getSite().getShell(),
                descriptor, registry);
        if (dialog.open() == Window.OK) {
            registry.updateConfiguration(descriptor.getId(), dialog.getConfiguration());
            refresh();
        }
    }

    public void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        HarnessRegistry registry = DStudioUiPlugin.getInstance().harnessRegistry();
        List<HarnessDescriptor> descriptors = registry == null ? List.of()
                : new ArrayList<>(registry.listHarnesses());
        viewer.setInput(descriptors);
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }
}
