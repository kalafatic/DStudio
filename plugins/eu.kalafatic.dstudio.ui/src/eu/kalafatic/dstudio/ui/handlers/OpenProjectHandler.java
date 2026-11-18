package eu.kalafatic.dstudio.ui.handlers;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.service.ProjectService;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import eu.kalafatic.dstudio.ui.explorer.ProjectExplorerView;
import eu.kalafatic.dstudio.ui.workspace.EclipseWorkspaceBridge;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

public class OpenProjectHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ProjectService projectService = DStudioUiPlugin.getInstance().projectService();
        if (projectService == null) {
            return null;
        }
        org.eclipse.swt.widgets.Shell shell = HandlerUtil.getActiveShell(event);
        if (shell == null) {
            org.eclipse.swt.widgets.Display display =
                    org.eclipse.swt.widgets.Display.getDefault();
            shell = display == null ? null : display.getActiveShell();
        }
        if (shell == null) {
            org.eclipse.ui.IWorkbenchWindow window = HandlerUtil
                    .getActiveWorkbenchWindow(event);
            if (window != null) {
                shell = window.getShell();
            }
        }
        if (shell == null) {
            return null;
        }
        DirectoryDialog dialog = new DirectoryDialog(shell);
        String path = dialog.open();
        if (path == null) {
            return null;
        }
        try {
            Project project = projectService.openProject(path);
            new EclipseWorkspaceBridge().ensureProject(project);
        } catch (Exception e) {
            org.eclipse.jface.dialogs.MessageDialog.openError(
                    HandlerUtil.getActiveShell(event), "Open Project",
                    "Failed to open project: " + e.getMessage());
            return null;
        }
        refreshExplorer(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage());
        return null;
    }

    static void refreshExplorer(IWorkbenchPage page) {
        if (page == null) {
            return;
        }
        IViewPart view = page.findView(ProjectExplorerView.VIEW_ID);
        if (view instanceof ProjectExplorerView explorer) {
            explorer.refresh();
        }
    }
}
