package eu.kalafatic.dstudio.ui.inference;

import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
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
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;

/**
 * Inference-engine management view (Phase 6, WP-6.7 section 7.3): engines, model
 * discovery, load/unload, runtime status and default selection — independent from the
 * harness view. ENR is the default engine; alternatives remain selectable.
 */
public class InferenceView extends ViewPart {

    public static final String VIEW_ID = "eu.kalafatic.dstudio.ui.view.inference";

    private TableViewer viewer;
    private TableViewer modelViewer;
    private Button selectButton;
    private Button enableButton;
    private Button testButton;
    private Button refreshButton;
    private Button loadButton;
    private Button unloadButton;

    @Override
    public void createPartControl(Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(4).equalWidth(true).applyTo(container);
        viewer = new TableViewer(container, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, false).span(4, 1)
                .applyTo(viewer.getTable());
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        TableViewerColumn idColumn = new TableViewerColumn(viewer, SWT.NONE);
        idColumn.getColumn().setText("Inference Engine");
        idColumn.getColumn().setWidth(180);
        idColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((InferenceEngineDescriptor) element).getName();
            }
        });
        TableViewerColumn typeColumn = new TableViewerColumn(viewer, SWT.NONE);
        typeColumn.getColumn().setText("Type");
        typeColumn.getColumn().setWidth(90);
        typeColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((InferenceEngineDescriptor) element).getType();
            }
        });
        TableViewerColumn enabledColumn = new TableViewerColumn(viewer, SWT.NONE);
        enabledColumn.getColumn().setText("Enabled");
        enabledColumn.getColumn().setWidth(70);
        enabledColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return String.valueOf(((InferenceEngineDescriptor) element).isEnabled());
            }
        });
        TableViewerColumn statusColumn = new TableViewerColumn(viewer, SWT.NONE);
        statusColumn.getColumn().setText("Status");
        statusColumn.getColumn().setWidth(100);
        statusColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((InferenceEngineDescriptor) element).getStatus().name();
            }
        });
        TableViewerColumn defaultColumn = new TableViewerColumn(viewer, SWT.NONE);
        defaultColumn.getColumn().setText("Default");
        defaultColumn.getColumn().setWidth(90);
        defaultColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                InferenceEngineRegistry registry = registry();
                InferenceEngineDescriptor descriptor = (InferenceEngineDescriptor) element;
                return registry != null && registry.getSelectedDefaultEngineId()
                        .map(descriptor.getId()::equals).orElse(false) ? "selected" : "";
            }
        });

        modelViewer = new TableViewer(container, SWT.SINGLE | SWT.FULL_SELECTION
                | SWT.BORDER);
        GridDataFactory.fillDefaults().grab(true, true).span(4, 1)
                .applyTo(modelViewer.getTable());
        modelViewer.setContentProvider(ArrayContentProvider.getInstance());
        modelViewer.getTable().setHeaderVisible(true);
        modelViewer.getTable().setLinesVisible(true);
        TableViewerColumn modelColumn = new TableViewerColumn(modelViewer, SWT.NONE);
        modelColumn.getColumn().setText("Model");
        modelColumn.getColumn().setWidth(240);
        modelColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((InferenceEngineAdapter.InferenceModel) element).id();
            }
        });
        TableViewerColumn engineColumn = new TableViewerColumn(modelViewer, SWT.NONE);
        engineColumn.getColumn().setText("Engine");
        engineColumn.getColumn().setWidth(120);
        engineColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((InferenceEngineAdapter.InferenceModel) element).engine();
            }
        });
        TableViewerColumn availableColumn = new TableViewerColumn(modelViewer, SWT.NONE);
        availableColumn.getColumn().setText("Available");
        availableColumn.getColumn().setWidth(90);
        availableColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return String.valueOf(
                        ((InferenceEngineAdapter.InferenceModel) element).available());
            }
        });

        selectButton = new Button(container, SWT.PUSH);
        selectButton.setText("Set Default");
        selectButton.addListener(SWT.Selection, e -> onSelectDefault());
        enableButton = new Button(container, SWT.PUSH);
        enableButton.setText("Enable/Disable");
        enableButton.addListener(SWT.Selection, e -> onToggleEnabled());
        testButton = new Button(container, SWT.PUSH);
        testButton.setText("Test Connection");
        testButton.addListener(SWT.Selection, e -> onTestConnection());
        refreshButton = new Button(container, SWT.PUSH);
        refreshButton.setText("Refresh Models");
        refreshButton.addListener(SWT.Selection, e -> onRefreshModels());
        loadButton = new Button(container, SWT.PUSH);
        loadButton.setText("Load Model");
        loadButton.addListener(SWT.Selection, e -> onModelLifecycle(true));
        unloadButton = new Button(container, SWT.PUSH);
        unloadButton.setText("Unload Model");
        unloadButton.addListener(SWT.Selection, e -> onModelLifecycle(false));

        // Unified contextual actions: provider menu on engine rows, model menu on
        // model rows, registry menu on empty engine selection.
        eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry contextualRegistry =
                DStudioUiPlugin.getInstance().contextualActionRegistry();
        if (contextualRegistry != null) {
            eu.kalafatic.dstudio.ui.contextual.ContextualMenuSupport.attach(
                    viewer.getTable(), contextualRegistry, this::providerSelection,
                    this::refresh);
            eu.kalafatic.dstudio.ui.contextual.ContextualMenuSupport.attach(
                    modelViewer.getTable(), contextualRegistry, this::modelSelection,
                    this::onRefreshModels);
        }

        refresh();
    }

    private eu.kalafatic.dstudio.core.contextual.ContextSelection providerSelection() {
        InferenceEngineDescriptor descriptor = selectedEngine();
        InferenceEngineRegistry registry = registry();
        if (descriptor == null) {
            return eu.kalafatic.dstudio.core.contextual.ContextSelection
                    .builder("DSTUDIO", "RCP", "provider-registry").build();
        }
        boolean isDefault = registry != null
                && registry.getSelectedDefaultEngineId().map(descriptor.getId()::equals)
                        .orElse(false);
        java.util.Set<String> capabilities = new java.util.LinkedHashSet<>();
        capabilities.add("model:load");
        capabilities.add("model:unload");
        return eu.kalafatic.dstudio.core.contextual.ContextSelection
                .builder("DSTUDIO", "RCP", "provider")
                .object(descriptor.getId(), descriptor.getName())
                .state("enabled", descriptor.isEnabled())
                .state("isDefault", isDefault)
                .capabilities(capabilities)
                .build();
    }

    private eu.kalafatic.dstudio.core.contextual.ContextSelection modelSelection() {
        IStructuredSelection selection = modelViewer.getStructuredSelection();
        if (selection.isEmpty()) {
            return eu.kalafatic.dstudio.core.contextual.ContextSelection
                    .builder("DSTUDIO", "RCP", "provider")
                    .object("", "").build();
        }
        InferenceEngineAdapter.InferenceModel model =
                (InferenceEngineAdapter.InferenceModel) selection.getFirstElement();
        InferenceEngineDescriptor engine = selectedEngine();
        String engineId = engine != null ? engine.getId() : model.engine();
        String defaultModel = DStudioUiPlugin.getInstance().coordinator() != null
                ? DStudioUiPlugin.getInstance().coordinator().getConfiguration()
                        .defaultModel()
                : "";
        return eu.kalafatic.dstudio.core.contextual.ContextSelection
                .builder("DSTUDIO", "RCP", "model")
                .object(model.id(), model.name())
                .providerId(engineId)
                .state("available", model.available())
                .state("isDefault", model.id().equals(defaultModel))
                .build();
    }

    private InferenceEngineRegistry registry() {
        return DStudioUiPlugin.getInstance().inferenceEngineRegistry();
    }

    private InferenceEngineDescriptor selectedEngine() {
        IStructuredSelection selection = viewer.getStructuredSelection();
        return selection.isEmpty() ? null
                : (InferenceEngineDescriptor) selection.getFirstElement();
    }

    private void onSelectDefault() {
        InferenceEngineDescriptor descriptor = selectedEngine();
        InferenceEngineRegistry registry = registry();
        if (descriptor != null && registry != null) {
            registry.selectDefaultEngine(descriptor.getId());
            refresh();
        }
    }

    private void onToggleEnabled() {
        InferenceEngineDescriptor descriptor = selectedEngine();
        InferenceEngineRegistry registry = registry();
        if (descriptor != null && registry != null) {
            registry.setEngineEnabled(descriptor.getId(), !descriptor.isEnabled());
            refresh();
        }
    }

    private void onTestConnection() {
        InferenceEngineDescriptor descriptor = selectedEngine();
        InferenceEngineRegistry registry = registry();
        if (descriptor != null && registry != null) {
            Display display = Display.getCurrent();
            if (display != null) {
                display.asyncExec(() -> {
                    var result = registry.testConnection(descriptor.getId());
                    org.eclipse.jface.dialogs.MessageDialog.openInformation(
                            getSite().getShell(), "Connection Test",
                            result.success() ? result.message() : result.message());
                    refresh();
                });
            }
        }
    }

    private void onRefreshModels() {
        InferenceEngineDescriptor descriptor = selectedEngine();
        InferenceEngineRegistry registry = registry();
        if (descriptor == null || registry == null) {
            return;
        }
        List<InferenceEngineAdapter.InferenceModel> models;
        try {
            models = new ArrayList<>(registry.findAdapter(descriptor.getId())
                    .map(InferenceEngineAdapter::listModels)
                    .orElse(List.of()));
        } catch (RuntimeException e) {
            models = new ArrayList<>();
        }
        modelViewer.setInput(models);
    }

    private void onModelLifecycle(boolean load) {
        InferenceEngineDescriptor engineDescriptor = selectedEngine();
        InferenceEngineRegistry registry = registry();
        IStructuredSelection selection = modelViewer.getStructuredSelection();
        if (engineDescriptor == null || registry == null || selection.isEmpty()) {
            return;
        }
        InferenceEngineAdapter.InferenceModel model =
                (InferenceEngineAdapter.InferenceModel) selection.getFirstElement();
        Display display = Display.getCurrent();
        if (display != null) {
            display.asyncExec(() -> {
                registry.findAdapter(engineDescriptor.getId()).ifPresent(adapter -> {
                    boolean success = load ? adapter.loadModel(model.id())
                            : adapter.unloadModel(model.id());
                    org.eclipse.jface.dialogs.MessageDialog.openInformation(
                            getSite().getShell(), load ? "Load Model" : "Unload Model",
                            success ? "Model " + model.id() + " operation succeeded"
                                    : "Model " + model.id() + " operation failed");
                });
            });
        }
    }

    public void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        InferenceEngineRegistry registry = registry();
        List<InferenceEngineDescriptor> descriptors = registry == null ? List.of()
                : new ArrayList<>(registry.listEngines());
        viewer.setInput(descriptors);
        modelViewer.setInput(new ArrayList<>());
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }
}
