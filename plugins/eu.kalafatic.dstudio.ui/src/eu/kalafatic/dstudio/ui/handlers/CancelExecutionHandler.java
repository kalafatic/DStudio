package eu.kalafatic.dstudio.ui.handlers;

import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.ui.DStudioUiPlugin;
import eu.kalafatic.dstudio.ui.execution.ExecutionView;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class CancelExecutionHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ExecutionService executionService = DStudioUiPlugin.getInstance().executionService();
        if (executionService == null) {
            return null;
        }
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (selection instanceof IStructuredSelection structured
                && !structured.isEmpty()
                && structured.getFirstElement() instanceof ExecutionTask task) {
            if (!task.isTerminal()) {
                executionService.cancel(task.getId());
            }
        }
        org.eclipse.ui.IViewPart view = HandlerUtil.getActiveWorkbenchWindow(event)
                .getActivePage().findView(ExecutionView.VIEW_ID);
        if (view instanceof ExecutionView executionView) {
            executionView.refresh();
        }
        return null;
    }
}
