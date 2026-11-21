package eu.kalafatic.dstudio.ui.workbench;

import eu.kalafatic.dstudio.application.DStudioRuntime;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import org.eclipse.swt.program.Program;

/**
 * Single browser-launch implementation shared by the Tools menu, the Project Explorer
 * context menu, the Help menu and the family contextual action {@code eu.kalafatic
 * .contextual.web.open}. Resolves the port the backend server is actually bound to —
 * never hardcodes it — and opens the URL in the system browser.
 */
public final class WebInterfaceLauncher {

    private WebInterfaceLauncher() {
    }

    /** Opens the web interface root (http://127.0.0.1:&lt;port&gt;/). */
    public static void openWebInterface() {
        launch("http://127.0.0.1:" + resolvePort() + "/");
    }

    /** Opens the embedded help site at the given anchor (e.g. {@code help.html#top}). */
    public static void openHelp(String anchor) {
        String target = anchor == null || anchor.isBlank() ? "help.html"
                : "help.html#" + anchor;
        launch("http://127.0.0.1:" + resolvePort() + "/" + target);
    }

    /** Opens the diagnostics endpoint (http://127.0.0.1:&lt;port&gt;/api/diagnostics). */
    public static void openDiagnostics() {
        launch("http://127.0.0.1:" + resolvePort() + "/api/diagnostics");
    }

    /**
     * Resolves the port the backend server is actually bound to; falls back to the
     * configured TEIP port and finally to the family default (via the coordinator —
     * the ui bundle never imports TEIP server types directly).
     */
    public static int resolvePort() {
        DStudioLifecycleCoordinator coordinator = DStudioRuntime.getCoordinator();
        return coordinator == null
                ? eu.kalafatic.dstudio.core.config.DStudioConfiguration.DEFAULT_TEIP_PORT
                : coordinator.getBoundTeipPort();
    }

    private static void launch(String url) {
        Program.launch(url);
    }
}
