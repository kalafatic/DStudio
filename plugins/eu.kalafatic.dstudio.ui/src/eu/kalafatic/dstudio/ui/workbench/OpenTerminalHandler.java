package eu.kalafatic.dstudio.ui.workbench;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

/**
 * Tools menu "Open Terminal" command: delegates to the shared {@link TerminalLauncher}
 * (working directory resolved from the active project, falling back to user home).
 */
public class OpenTerminalHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        TerminalLauncher.open(null);
        return null;
    }
}
