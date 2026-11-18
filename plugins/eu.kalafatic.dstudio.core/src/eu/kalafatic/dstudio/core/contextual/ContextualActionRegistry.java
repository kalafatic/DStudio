package eu.kalafatic.dstudio.core.contextual;

import java.util.List;
import java.util.Optional;

/**
 * The one contextual action registry of the application (Darwin Family Contextual Action
 * Contract v1.0, §1 rule 4: no duplicate registries). Providers contribute availability,
 * handlers execute. Both RCP and Web surfaces resolve and execute through this single
 * registry.
 */
public interface ContextualActionRegistry {

    void registerProvider(ContextualActionProvider provider);

    void unregisterProvider(ContextualActionProvider provider);

    void registerHandler(ContextualActionHandler handler);

    void unregisterHandler(String actionId, String surface);

    /** Resolves all actions for the selection, ordered by group and label. */
    List<ContextualAction> resolve(ContextSelection selection);

    /** Resolves a single action id for the selection. */
    Optional<ContextualAction> resolve(String actionId, ContextSelection selection);

    /**
     * Executes an action after re-evaluating availability. Returns a structured result
     * instead of throwing, so Web surfaces can map it to JSON.
     */
    ContextualExecutionResult execute(String actionId, ContextSelection selection);

    /** Re-executes an action that was already validated (skips the availability gate). */
    ContextualExecutionResult executeResolved(ContextualAction action, ContextSelection selection);
}
