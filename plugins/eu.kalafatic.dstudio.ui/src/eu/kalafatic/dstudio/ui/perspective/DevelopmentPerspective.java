package eu.kalafatic.dstudio.ui.perspective;

import eu.kalafatic.dstudio.ui.execution.ExecutionView;
import eu.kalafatic.dstudio.ui.explorer.ProjectExplorerView;
import eu.kalafatic.dstudio.ui.harness.HarnessView;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class DevelopmentPerspective implements IPerspectiveFactory {

    public static final String ID = "eu.kalafatic.dstudio.ui.perspective.development";

    @Override
    public void createInitialLayout(IPageLayout layout) {
        String editorArea = layout.getEditorArea();
        layout.setEditorAreaVisible(true);
        IFolderLayout left = layout.createFolder("left",
                IPageLayout.LEFT, 0.22f, editorArea);
        left.addView(ProjectExplorerView.VIEW_ID);
        IFolderLayout bottom = layout.createFolder("bottom",
                IPageLayout.BOTTOM, 0.74f, editorArea);
        bottom.addView("org.eclipse.ui.console.ConsoleView");
        bottom.addView("org.eclipse.ui.views.ProblemView");
        IFolderLayout right = layout.createFolder("right",
                IPageLayout.RIGHT, 0.72f, editorArea);
        right.addView(ExecutionView.VIEW_ID);
        right.addView(HarnessView.VIEW_ID);
        layout.getViewLayout(ProjectExplorerView.VIEW_ID).setCloseable(false);
        layout.getViewLayout("org.eclipse.ui.console.ConsoleView").setCloseable(false);
        layout.addPerspectiveShortcut(DevelopmentPerspective.ID);
        layout.addPerspectiveShortcut(HarnessPerspective.ID);
        layout.addPerspectiveShortcut(ReviewPerspective.ID);
        layout.addPerspectiveShortcut(RepositoryPerspective.ID);
    }
}
