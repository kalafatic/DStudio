package eu.kalafatic.dstudio.ui.perspective;

import eu.kalafatic.dstudio.ui.agent.AgentView;
import eu.kalafatic.dstudio.ui.execution.ExecutionView;
import eu.kalafatic.dstudio.ui.harness.HarnessView;
import eu.kalafatic.dstudio.ui.inference.InferenceView;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class HarnessPerspective implements IPerspectiveFactory {

    public static final String ID = "eu.kalafatic.dstudio.ui.perspective.harness";

    @Override
    public void createInitialLayout(IPageLayout layout) {
        String editorArea = layout.getEditorArea();
        layout.setEditorAreaVisible(false);
        IFolderLayout left = layout.createFolder("left",
                IPageLayout.LEFT, 0.30f, editorArea);
        left.addView(HarnessView.VIEW_ID);
        left.addView(InferenceView.VIEW_ID);
        left.addView(AgentView.VIEW_ID);
        IFolderLayout right = layout.createFolder("right",
                IPageLayout.RIGHT, 0.45f, editorArea);
        right.addView(ExecutionView.VIEW_ID);
        IFolderLayout bottom = layout.createFolder("bottom",
                IPageLayout.BOTTOM, 0.70f, "left");
        bottom.addView("org.eclipse.ui.console.ConsoleView");
        layout.addPerspectiveShortcut(DevelopmentPerspective.ID);
    }
}
