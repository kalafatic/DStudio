package eu.kalafatic.dstudio.ui;

import eu.kalafatic.dstudio.core.event.EventBus;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.ui.console.ExecutionConsoleFactory;
import eu.kalafatic.dstudio.ui.workspace.EclipseWorkspaceBridge;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

public class DStudioUiStartup implements IStartup {

    private AutoCloseable eventSubscription;
    private static volatile boolean contextualActionsInstalled = false;

    @Override
    public void earlyStartup() {
        DStudioUiPlugin plugin = DStudioUiPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        // Register the RCP-surface handlers of the unified contextual action
        // framework once per workbench session (dialogs, clipboard, explorer).
        if (!contextualActionsInstalled) {
            contextualActionsInstalled = true;
            eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry registry =
                    plugin.contextualActionRegistry();
            if (registry != null) {
                eu.kalafatic.dstudio.ui.contextual.RcpContextualActions.install(registry);
            }
        }
        org.eclipse.ui.IWorkbench workbench = org.eclipse.ui.PlatformUI.getWorkbench();
        if (workbench != null) {
            workbench.getDisplay().asyncExec(() -> {
                new EclipseWorkspaceBridge().synchronize(plugin.projectService());
            });
        }
        EventBus eventBus = plugin.eventBus();
        if (eventBus != null) {
            eventSubscription = eventBus.subscribe(ExecutionEvent.class,
                    DStudioUiStartup::forwardToConsole);
        }
    }

    private static void forwardToConsole(ExecutionEvent event) {
        if (event.type() == ExecutionEventType.EXECUTION_LOG
                || event.type() == ExecutionEventType.EXECUTION_QUEUED
                || event.type() == ExecutionEventType.EXECUTION_STARTED
                || event.type() == ExecutionEventType.EXECUTION_COMPLETED
                || event.type() == ExecutionEventType.EXECUTION_FAILED
                || event.type() == ExecutionEventType.EXECUTION_CANCELLED
                || event.type() == ExecutionEventType.TOOL_INVOKED) {
            org.eclipse.ui.IWorkbench workbench = org.eclipse.ui.PlatformUI.getWorkbench();
            if (workbench == null || workbench.getDisplay() == null) {
                return;
            }
            workbench.getDisplay().asyncExec(() -> {
                MessageConsole console = ExecutionConsoleFactory.showConsole();
                MessageConsoleStream stream = console.newMessageStream();
                try {
                    stream.println("[" + event.executionId() + "] "
                            + event.type().name() + ": " + event.message());
                } finally {
                    try {
                        stream.close();
                    } catch (java.io.IOException e) {
                        System.err.println("DStudio: console stream close failed: "
                                + e.getMessage());
                    }
                }
            });
        }
    }
}
