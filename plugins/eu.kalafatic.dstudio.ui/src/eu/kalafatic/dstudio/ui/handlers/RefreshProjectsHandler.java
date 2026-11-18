package eu.kalafatic.dstudio.ui.handlers;

import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import eu.kalafatic.dstudio.ui.workspace.EclipseWorkspaceBridge;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

public class RefreshProjectsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        DStudioUiPlugin plugin = DStudioUiPlugin.getInstance();
        if (plugin != null) {
            new EclipseWorkspaceBridge().synchronize(plugin.projectService());
        }
        OpenProjectHandler.refreshExplorer(
                HandlerUtil.getActiveWorkbenchWindow(event).getActivePage());
        return null;
    }
}
