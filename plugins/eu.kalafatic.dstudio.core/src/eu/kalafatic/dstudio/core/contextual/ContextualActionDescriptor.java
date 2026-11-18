package eu.kalafatic.dstudio.core.contextual;

import java.util.List;

/**
 * Immutable action definition (Darwin Family Contextual Action Contract v1.0).
 * Descriptors are data only: availability is computed per selection by providers,
 * execution is performed by registered handlers. RCP and Web surfaces render the
 * same descriptors with the same ids.
 */
public record ContextualActionDescriptor(
        String contractVersion,
        String actionId,
        String label,
        String description,
        String group,
        List<String> contextTypes,
        List<String> requiredCapabilities,
        Confirmation confirmation,
        boolean dangerous) {

    public enum Confirmation {
        NONE, CONFIRM, DANGEROUS
    }

    public static final String GROUP_SELECTION = "selection";
    public static final String GROUP_CONFIGURATION = "configuration";
    public static final String GROUP_LIFECYCLE = "lifecycle";
    public static final String GROUP_INSPECTION = "inspection";
    public static final String GROUP_DANGER = "danger";

    public ContextualActionDescriptor {
        contractVersion = contractVersion == null ? ContextActionIds.CONTRACT_VERSION
                : contractVersion;
        contextTypes = List.copyOf(contextTypes == null ? List.of() : contextTypes);
        requiredCapabilities = List.copyOf(requiredCapabilities == null ? List.of()
                : requiredCapabilities);
        confirmation = confirmation == null ? Confirmation.NONE : confirmation;
    }

    public static ContextualActionDescriptor of(String actionId, String label,
            String group, String... contextTypes) {
        return new ContextualActionDescriptor(ContextActionIds.CONTRACT_VERSION, actionId,
                label, "", group, List.of(contextTypes), List.of(), Confirmation.NONE, false);
    }

    public ContextualActionDescriptor withDescription(String text) {
        return new ContextualActionDescriptor(contractVersion, actionId, label, text, group,
                contextTypes, requiredCapabilities, confirmation, dangerous);
    }

    public ContextualActionDescriptor withCapabilities(List<String> capabilities) {
        return new ContextualActionDescriptor(contractVersion, actionId, label, description,
                group, contextTypes, capabilities, confirmation, dangerous);
    }

    public ContextualActionDescriptor withConfirmation(Confirmation value) {
        return new ContextualActionDescriptor(contractVersion, actionId, label, description,
                group, contextTypes, requiredCapabilities, value,
                value == Confirmation.DANGEROUS || dangerous);
    }
}
