package eu.kalafatic.dstudio.ui.perspective;

import eu.kalafatic.dstudio.ui.execution.ExecutionView;
import eu.kalafatic.dstudio.ui.review.ReviewView;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class ReviewPerspective implements IPerspectiveFactory {

    public static final String ID = "eu.kalafatic.dstudio.ui.perspective.review";

    @Override
    public void createInitialLayout(IPageLayout layout) {
        String editorArea = layout.getEditorArea();
        layout.setEditorAreaVisible(true);
        IFolderLayout left = layout.createFolder("left",
                IPageLayout.LEFT, 0.42f, editorArea);
        left.addView(ReviewView.VIEW_ID);
        left.addView(ExecutionView.VIEW_ID);
        layout.addPerspectiveShortcut(DevelopmentPerspective.ID);
    }
}
