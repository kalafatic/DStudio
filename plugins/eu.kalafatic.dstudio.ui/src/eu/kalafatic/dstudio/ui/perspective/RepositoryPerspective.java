package eu.kalafatic.dstudio.ui.perspective;

import eu.kalafatic.dstudio.ui.explorer.ProjectExplorerView;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class RepositoryPerspective implements IPerspectiveFactory {

    public static final String ID = "eu.kalafatic.dstudio.ui.perspective.repository";

    @Override
    public void createInitialLayout(IPageLayout layout) {
        String editorArea = layout.getEditorArea();
        layout.setEditorAreaVisible(false);
        IFolderLayout left = layout.createFolder("left",
                IPageLayout.LEFT, 0.30f, editorArea);
        left.addView(ProjectExplorerView.VIEW_ID);
        left.addView("org.eclipse.egit.ui.RepositoriesView");
        IFolderLayout right = layout.createFolder("right",
                IPageLayout.RIGHT, 0.50f, editorArea);
        right.addView("org.eclipse.egit.ui.HistoryView");
        right.addView("org.eclipse.egit.ui.StagingView");
        layout.addPerspectiveShortcut(DevelopmentPerspective.ID);
    }
}
