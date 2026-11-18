package eu.kalafatic.dstudio.core.contextual;

import java.util.List;

/**
 * Contributes actions (descriptors + availability) for one or more context types.
 * Providers are pure evaluators over {@link ContextSelection}; they never execute
 * business operations themselves — execution always goes through registered
 * {@link ContextualActionHandler}s.
 */
public interface ContextualActionProvider {

    /** Context types this provider contributes for (e.g. "harness", "harness-registry"). */
    List<String> contextTypes();

    /** Computes the available actions for the selection (may be empty). */
    List<ContextualAction> actionsFor(ContextSelection selection);
}
