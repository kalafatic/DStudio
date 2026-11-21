package eu.kalafatic.dstudio.ui.workbench;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

/**
 * Help menu command handler: opens the embedded help site (served by the backend at
 * {@code /help.html}) at the anchor belonging to the executed help command. The F1 key
 * handler and the Help menu items run through this single code path.
 */
public class OpenHelpHandler extends AbstractHandler {

    public static final String COMMAND_CONTENTS = "eu.kalafatic.dstudio.command.help.contents";
    public static final String COMMAND_GETTING_STARTED =
            "eu.kalafatic.dstudio.command.help.gettingStarted";
    public static final String COMMAND_CONTEXT = "eu.kalafatic.dstudio.command.help.context";
    public static final String COMMAND_SHORTCUTS = "eu.kalafatic.dstudio.command.help.shortcuts";
    public static final String COMMAND_CLI = "eu.kalafatic.dstudio.command.help.cli";
    public static final String COMMAND_DIAGNOSTICS =
            "eu.kalafatic.dstudio.command.help.diagnostics";

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        String commandId = event.getCommand() == null ? "" : event.getCommand().getId();
        switch (commandId) {
            case COMMAND_CONTENTS -> WebInterfaceLauncher.openHelp("top");
            case COMMAND_GETTING_STARTED -> WebInterfaceLauncher.openHelp("getting-started");
            case COMMAND_CONTEXT -> HelpLauncher.openContextHelp();
            case COMMAND_SHORTCUTS -> WebInterfaceLauncher.openHelp("shortcuts");
            case COMMAND_CLI -> WebInterfaceLauncher.openHelp("cli");
            case COMMAND_DIAGNOSTICS -> WebInterfaceLauncher.openDiagnostics();
            default -> WebInterfaceLauncher.openHelp("top");
        }
        return null;
    }
}
