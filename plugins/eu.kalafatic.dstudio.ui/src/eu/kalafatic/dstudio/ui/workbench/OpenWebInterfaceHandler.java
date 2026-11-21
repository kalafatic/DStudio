package eu.kalafatic.dstudio.ui.workbench;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

/**
 * Tools menu "Open Web Interface" command: delegates to the shared
 * {@link WebInterfaceLauncher}.
 */
public class OpenWebInterfaceHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        WebInterfaceLauncher.openWebInterface();
        return null;
    }
}
