package eu.kalafatic.dstudio.ui.contextual;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.contextual.ContextualExecutionResult;

/**
 * RCP rendering of the unified contextual action framework: turns the registry's
 * resolved actions into a JFace MenuManager, grouped and separated per contract §13.
 * Confirmation and error feedback are handled here; business logic stays in the
 * registered handlers.
 */
public final class ContextualMenuSupport {

    private ContextualMenuSupport() {
    }

    public static MenuManager attach(Control control, ContextualActionRegistry registry,
            Supplier<ContextSelection> selectionSupplier, Runnable afterExecution) {
        MenuManager menuManager = new MenuManager();
        menuManager.setRemoveAllWhenShown(true);
        menuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                ContextSelection selection = selectionSupplier.get();
                if (selection == null) {
                    return;
                }
                fill(manager, registry.resolve(selection), selection, control.getShell(),
                        afterExecution);
            }
        });
        Menu menu = menuManager.createContextMenu(control);
        control.setMenu(menu);
        return menuManager;
    }

    public static void fill(IMenuManager manager, List<ContextualAction> actions,
            ContextSelection selection, Shell shell, Runnable afterExecution) {
        ContextualActionRegistry registry = registry(selection);
        if (registry == null || actions.isEmpty()) {
            return;
        }
        String currentGroup = null;
        for (ContextualAction action : actions) {
            String group = action.descriptor().group();
            if (!group.equals(currentGroup)) {
                if (currentGroup != null) {
                    manager.add(new Separator());
                }
                currentGroup = group;
            }
            manager.add(toJFaceAction(action, selection, shell, afterExecution, registry));
        }
    }

    private static ContextualActionRegistry registry(ContextSelection selection) {
        eu.kalafatic.dstudio.ui.DStudioUiPlugin plugin =
                eu.kalafatic.dstudio.ui.DStudioUiPlugin.getInstance();
        return plugin == null ? null : plugin.contextualActionRegistry();
    }

    private static Action toJFaceAction(ContextualAction action, ContextSelection selection,
            Shell shell, Runnable afterExecution, ContextualActionRegistry registry) {
        ContextualActionDescriptor descriptor = action.descriptor();
        Action jfaceAction = new Action(descriptor.label()) {
            @Override
            public void run() {
                if (descriptor.confirmation()
                        == ContextualActionDescriptor.Confirmation.CONFIRM
                        || descriptor.confirmation()
                                == ContextualActionDescriptor.Confirmation.DANGEROUS) {
                    boolean confirmed = MessageDialog.openConfirm(shell,
                            descriptor.label(),
                            "Apply '" + descriptor.label() + "' to "
                                    + (selection.objectLabel().isBlank()
                                            ? selection.objectId() : selection.objectLabel())
                                    + "?");
                    if (!confirmed) {
                        return;
                    }
                }
                ContextualExecutionResult result = registry.executeResolved(action,
                        selection);
                if (!result.ok()) {
                    MessageDialog.openError(shell, descriptor.label(),
                            result.message());
                    return;
                }
                if (afterExecution != null) {
                    afterExecution.run();
                }
            }
        };
        jfaceAction.setId(descriptor.actionId());
        String tooltip = descriptor.description().isBlank() ? descriptor.label()
                : descriptor.description();
        if (!action.enabled()) {
            jfaceAction.setEnabled(false);
            if (!action.disabledReason().isBlank()) {
                tooltip = tooltip + " — " + action.disabledReason();
            }
        }
        jfaceAction.setToolTipText(tooltip);
        return jfaceAction;
    }
}
