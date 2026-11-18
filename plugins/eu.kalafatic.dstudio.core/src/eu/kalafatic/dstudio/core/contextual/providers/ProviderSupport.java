package eu.kalafatic.dstudio.core.contextual.providers;

import java.util.List;
import java.util.Optional;

import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionProvider;

/**
 * Shared helpers for providers: descriptor construction, availability gating and
 * capability checks. Kept package-private — availability logic is provider-local.
 */
final class ProviderSupport {

    static final String CTX_HARNESS_REGISTRY = "harness-registry";
    static final String CTX_HARNESS = "harness";
    static final String CTX_PROVIDER_REGISTRY = "provider-registry";
    static final String CTX_PROVIDER = "provider";
    static final String CTX_MODEL = "model";
    static final String CTX_AGENT_REGISTRY = "agent-registry";
    static final String CTX_AGENT = "agent";
    static final String CTX_EXECUTION = "execution";
    static final String CTX_TASK = "task";
    static final String CTX_SESSION = "session";
    static final String CTX_PROJECT = "project";
    static final String CTX_REPOSITORY = "repository";

    static final String CAP_LIFECYCLE_START = "lifecycle:start";
    static final String CAP_LIFECYCLE_STOP = "lifecycle:stop";
    static final String CAP_LOGS = "logs";
    static final String CAP_MODEL_DOWNLOAD = "model:download";
    static final String CAP_MODEL_LOAD = "model:load";
    static final String CAP_MODEL_UNLOAD = "model:unload";
    static final String CAP_MODEL_REMOVE = "model:remove";

    private ProviderSupport() {
    }

    static ContextualActionDescriptor descriptor(String actionId, String label, String group,
            String... contextTypes) {
        return ContextualActionDescriptor.of(actionId, label, group, contextTypes);
    }

    static ContextualAction enabled(ContextualActionDescriptor descriptor) {
        return ContextualAction.enabled(descriptor);
    }

    /**
     * Surface-gated action: enabled on {@code enabledSurface}, otherwise disabled with
     * an honest "Available in the ..." reason (dialog/clipboard actions are RCP-side;
     * JSON configuration actions are WEB-side).
     */
    static ContextualAction surfaceGated(ContextualActionDescriptor descriptor,
            ContextSelection selection, String enabledSurface) {
        if (enabledSurface.equals(selection.surface())) {
            return ContextualAction.enabled(descriptor);
        }
        return ContextualAction.disabled(descriptor,
                "Available in the " + (ContextSelection.SURFACE_RCP.equals(enabledSurface)
                        ? "RCP application" : "web interface"));
    }

    static boolean hasCapability(ContextSelection selection, String capability) {
        return selection.capabilities().contains(capability);
    }

    static Optional<Boolean> stateBoolean(ContextSelection selection, String key) {
        String value = selection.state(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Boolean.parseBoolean(value));
    }

    static ContextualAction disabled(ContextualActionDescriptor descriptor, String reason) {
        return ContextualAction.disabled(descriptor, reason);
    }

    static List<String> list(String... values) {
        return List.of(values);
    }
}
