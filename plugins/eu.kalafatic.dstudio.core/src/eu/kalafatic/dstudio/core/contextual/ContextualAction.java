package eu.kalafatic.dstudio.core.contextual;

/**
 * A resolved action for a concrete selection: descriptor + availability. Actions that
 * are disabled remain visible (with {@link #disabledReason()}) so users understand why
 * an operation is unavailable; actions that are meaningless for the context are simply
 * not contributed.
 */
public record ContextualAction(
        ContextualActionDescriptor descriptor,
        boolean enabled,
        String disabledReason) {

    public static ContextualAction enabled(ContextualActionDescriptor descriptor) {
        return new ContextualAction(descriptor, true, "");
    }

    public static ContextualAction disabled(ContextualActionDescriptor descriptor,
            String reason) {
        return new ContextualAction(descriptor, false, reason == null ? "" : reason);
    }

    public String actionId() {
        return descriptor.actionId();
    }
}
