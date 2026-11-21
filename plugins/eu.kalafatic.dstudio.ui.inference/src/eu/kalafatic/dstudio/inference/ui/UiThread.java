package eu.kalafatic.dstudio.inference.ui;

import org.eclipse.swt.widgets.Display;

/** Display-thread marshalling helper for the presentation bundle. */
public final class UiThread {

    private UiThread() {
    }

    public static void async(Runnable runnable) {
        Display display = Display.getCurrent();
        if (display != null && !display.isDisposed()) {
            runnable.run();
            return;
        }
        Display def = Display.getDefault();
        if (def == null || def.isDisposed()) {
            return;
        }
        def.asyncExec(runnable);
    }

    /** Runs the runnable synchronously on the display thread (no-op when already there). */
    public static void sync(Runnable runnable) {
        if (Display.getCurrent() != null) {
            runnable.run();
            return;
        }
        Display def = Display.getDefault();
        if (def == null || def.isDisposed()) {
            return;
        }
        def.syncExec(runnable);
    }
}
