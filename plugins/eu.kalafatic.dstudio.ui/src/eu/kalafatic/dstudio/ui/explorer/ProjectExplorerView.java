package eu.kalafatic.dstudio.ui.explorer;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import eu.kalafatic.dstudio.ui.workspace.EclipseWorkspaceBridge;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

public class ProjectExplorerView extends ViewPart {

    public static final String VIEW_ID = "eu.kalafatic.dstudio.ui.view.projectExplorer";

    private TreeViewer viewer;
    private final ProjectExplorerContentProvider contentProvider =
            new ProjectExplorerContentProvider(null);

    @Override
    public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
        contentProvider.setProjectService(DStudioUiPlugin.getInstance().projectService());
        viewer.setContentProvider(contentProvider);
        viewer.setLabelProvider(new ProjectExplorerLabelProvider());
        viewer.setInput("root");
        getSite().setSelectionProvider(viewer);
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            @Override
            public void doubleClick(DoubleClickEvent event) {
                openSelection(event.getSelection());
            }
        });
        MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                Project project = selectedProject();
                if (project != null) {
                    // Unified contextual actions for the project (same ids and domain
                    // operations as the web interface).
                    eu.kalafatic.dstudio.core.contextual.ContextSelection selection =
                            eu.kalafatic.dstudio.core.contextual.ContextSelection
                                    .builder("DSTUDIO", "RCP", "project")
                                    .object(project.id(), project.name())
                                    .state("rootPath", project.rootPath())
                                    .build();
                    eu.kalafatic.dstudio.ui.contextual.ContextualMenuSupport.fill(manager,
                            DStudioUiPlugin.getInstance().contextualActionRegistry()
                                    .resolve(selection),
                            selection, getSite().getShell(), ProjectExplorerView.this::refresh);
                    manager.add(new org.eclipse.jface.action.Action("Refresh") {
                        @Override
                        public void run() {
                            refresh();
                        }
                    });
                } else {
                    manager.add(new org.eclipse.jface.action.Action("Open Project…") {
                        @Override
                        public void run() {
                            org.eclipse.ui.handlers.IHandlerService handlerService =
                                    getSite().getService(
                                            org.eclipse.ui.handlers.IHandlerService.class);
                            if (handlerService != null) {
                                try {
                                    handlerService.executeCommand(
                                            "eu.kalafatic.dstudio.command.openProject", null);
                                } catch (Exception e) {
                                    System.err.println("DStudio: open command failed: "
                                            + e.getMessage());
                                }
                            }
                        }
                    });
                }
            }
        });
        Menu menu = menuManager.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuManager, viewer);
    }

    private void openSelection(ISelection selection) {
        if (!(selection instanceof IStructuredSelection structured)
                || structured.isEmpty()) {
            return;
        }
        Object element = structured.getFirstElement();
        if (element instanceof ProjectExplorerContentProvider.ProjectFileEntry entry
                && entry.file().isFile()) {
            IWorkbenchPage page = getSite().getPage();
            try {
                IDE.openEditorOnFileStore(page, EFS.getStore(entry.file().toURI()));
            } catch (Exception e) {
                System.err.println("DStudio: failed to open editor for "
                        + entry.file() + ": " + e.getMessage());
            }
        }
    }

    public void refresh() {
        if (viewer != null && !viewer.getControl().isDisposed()) {
            viewer.refresh();
        }
    }

    public Project selectedProject() {
        ISelection selection = viewer.getSelection();
        if (selection instanceof IStructuredSelection structured
                && !structured.isEmpty()) {
            Object element = structured.getFirstElement();
            if (element instanceof Project project) {
                return project;
            }
            if (element instanceof ProjectExplorerContentProvider.ProjectFileEntry entry) {
                return entry.project();
            }
        }
        return null;
    }

    public String selectedFilePath() {
        ISelection selection = viewer.getSelection();
        if (selection instanceof IStructuredSelection structured
                && !structured.isEmpty()
                && structured.getFirstElement()
                        instanceof ProjectExplorerContentProvider.ProjectFileEntry entry) {
            return entry.file().getAbsolutePath();
        }
        return null;
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }
}
