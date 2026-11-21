package eu.kalafatic.dstudio.ui.workbench;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Context help entry point shared by the F1 key handler and the Help menu's
 * "Context Help" item: opens the embedded help site at the anchor describing the
 * currently active view. Views without a dedicated anchor fall back to the general
 * {@code #context} section.
 */
public final class HelpLauncher {

    private HelpLauncher() {
    }

    /** Opens context help for the active view of the active workbench window. */
    public static void openContextHelp() {
        String viewId = activeViewId();
        WebInterfaceLauncher.openHelp(anchorForViewId(viewId));
    }

    /** Maps a workbench part id to its help anchor ("context" when unknown). */
    public static String anchorForViewId(String viewId) {
        if (viewId == null) {
            return "context";
        }
        return switch (viewId) {
            case "eu.kalafatic.dstudio.ui.view.projectExplorer" -> "explorer";
            case "eu.kalafatic.dstudio.ui.view.harness" -> "harness";
            case "eu.kalafatic.dstudio.ui.view.execution" -> "execution";
            case "eu.kalafatic.dstudio.ui.view.inference" -> "inference";
            case "eu.kalafatic.dstudio.ui.view.agents" -> "agents";
            case "eu.kalafatic.dstudio.ui.view.review" -> "review";
            default -> "context";
        };
    }

    private static String activeViewId() {
        IWorkbenchWindow window = PlatformUI.getWorkbench() == null ? null
                : PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        IWorkbenchPage page = window == null ? null : window.getActivePage();
        IWorkbenchPart part = page == null ? null : page.getActivePart();
        return part == null ? null : part.getSite().getId();
    }
}
