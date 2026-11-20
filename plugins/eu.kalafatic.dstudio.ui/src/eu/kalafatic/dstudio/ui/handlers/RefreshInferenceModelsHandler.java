package eu.kalafatic.dstudio.ui.handlers;

import eu.kalafatic.dstudio.ui.inference.InferenceView;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * Shows the Inference view and triggers model discovery. This is the menu-bar
 * counterpart of the view's "Refresh Models" button; the command lives in the UI
 * bundle because model discovery is view-owned, while the menu contribution lives
 * in the application bundle (layering ui → application).
 */
public class RefreshInferenceModelsHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            IWorkbenchPage page = PlatformUI.getWorkbench()
                    .getActiveWorkbenchWindow().getActivePage();
            if (page != null) {
                InferenceView view = (InferenceView) page.showView(InferenceView.VIEW_ID);
                view.refreshModels();
            }
        } catch (PartInitException e) {
            throw new ExecutionException("Failed to show the Inference view", e);
        }
        return null;
    }
}
