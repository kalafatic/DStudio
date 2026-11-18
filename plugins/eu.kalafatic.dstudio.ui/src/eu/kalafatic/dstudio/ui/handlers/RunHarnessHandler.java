package eu.kalafatic.dstudio.ui.handlers;

import eu.kalafatic.dstudio.ui.execution.RunHarnessDialog;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.handlers.HandlerUtil;

public class RunHarnessHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        RunHarnessDialog dialog = new RunHarnessDialog(HandlerUtil.getActiveShell(event));
        dialog.open();
        return null;
    }
}
