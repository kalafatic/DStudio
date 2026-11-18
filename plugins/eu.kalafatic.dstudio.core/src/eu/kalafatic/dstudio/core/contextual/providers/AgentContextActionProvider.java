package eu.kalafatic.dstudio.core.contextual.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import eu.kalafatic.dstudio.core.agent.AgentProfileRegistry;
import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionProvider;

/**
 * Contextual actions for agent profiles. Agent selection is independent of harness and
 * provider selection; compatibility is validated against the ambient execution
 * environment through {@link AgentProfileRegistry#checkCompatibility}.
 */
public class AgentContextActionProvider implements ContextualActionProvider {

    private final AgentProfileRegistry registry;

    public AgentContextActionProvider(AgentProfileRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<String> contextTypes() {
        return ProviderSupport.list(ProviderSupport.CTX_AGENT, ProviderSupport.CTX_AGENT_REGISTRY);
    }

    @Override
    public List<ContextualAction> actionsFor(ContextSelection selection) {
        if (ProviderSupport.CTX_AGENT_REGISTRY.equals(selection.objectType())) {
            return registryActions(selection);
        }
        return agentActions(selection);
    }

    private List<ContextualAction> registryActions(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        ContextualActionDescriptor create = ProviderSupport.descriptor(
                ContextActionIds.AGENT_REGISTRY_CREATE, "Create Agent",
                ContextualActionDescriptor.GROUP_CONFIGURATION,
                ProviderSupport.CTX_AGENT_REGISTRY);
        actions.add(ProviderSupport.enabled(create));

        ContextualActionDescriptor importAction = ProviderSupport.descriptor(
                ContextActionIds.AGENT_REGISTRY_IMPORT, "Import Agent",
                ContextualActionDescriptor.GROUP_CONFIGURATION,
                ProviderSupport.CTX_AGENT_REGISTRY);
        actions.add(ProviderSupport.enabled(importAction));
        return actions;
    }

    private List<ContextualAction> agentActions(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        String profileId = selection.objectId();
        Optional<AgentProfile> profileOpt = registry == null ? Optional.empty()
                : registry.findProfile(profileId);
        if (profileOpt.isEmpty()) {
            return actions;
        }
        boolean isDefault = registry.getDefaultProfileId().map(profileId::equals)
                .orElse(false);
        boolean single = !selection.multiSelect();

        ContextualActionDescriptor setDefault = ProviderSupport.descriptor(
                ContextActionIds.AGENT_SET_DEFAULT, "Set as Default",
                ContextualActionDescriptor.GROUP_SELECTION, ProviderSupport.CTX_AGENT);
        actions.add(single && !isDefault ? ProviderSupport.enabled(setDefault)
                : ProviderSupport.disabled(setDefault, !single
                        ? "Not available for multiple selection"
                        : "Already the default agent"));

        ContextualActionDescriptor edit = ProviderSupport.descriptor(
                ContextActionIds.AGENT_EDIT, "Edit",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_AGENT);
        actions.add(single ? ProviderSupport.surfaceGated(edit, selection,
                ContextSelection.SURFACE_RCP) : ProviderSupport.disabled(edit,
                        "Not available for multiple selection"));

        ContextualActionDescriptor clone = ProviderSupport.descriptor(
                ContextActionIds.AGENT_CLONE, "Clone",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_AGENT);
        actions.add(single ? ProviderSupport.enabled(clone)
                : ProviderSupport.disabled(clone, "Not available for multiple selection"));

        // Instruction/tool/permission configuration is part of profile editing; the
        // three ids share one real handler so the contract ids remain addressable.
        for (String id : new String[] { ContextActionIds.AGENT_CONFIGURE_INSTRUCTIONS,
                ContextActionIds.AGENT_CONFIGURE_TOOLS,
                ContextActionIds.AGENT_CONFIGURE_PERMISSIONS }) {
            String label = id.equals(ContextActionIds.AGENT_CONFIGURE_INSTRUCTIONS)
                    ? "Configure Instructions"
                    : id.equals(ContextActionIds.AGENT_CONFIGURE_TOOLS)
                            ? "Configure Tools" : "Configure Permissions";
            ContextualActionDescriptor descriptor = ProviderSupport.descriptor(id, label,
                    ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_AGENT);
            actions.add(single ? ProviderSupport.surfaceGated(descriptor, selection,
                    ContextSelection.SURFACE_RCP) : ProviderSupport.disabled(descriptor,
                            "Not available for multiple selection"));
        }

        ContextualActionDescriptor exportAction = ProviderSupport.descriptor(
                ContextActionIds.AGENT_EXPORT, "Export",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_AGENT);
        actions.add(single ? ProviderSupport.enabled(exportAction)
                : ProviderSupport.disabled(exportAction, "Not available for multiple "
                        + "selection"));

        ContextualActionDescriptor compatibility = ProviderSupport.descriptor(
                ContextActionIds.AGENT_COMPATIBILITY, "Inspect Compatibility",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_AGENT)
                .withDescription("Validates this agent against the selected harness, "
                        + "provider and model.");
        boolean ambientSet = !selection.harnessId().isBlank()
                || !selection.providerId().isBlank();
        actions.add(single && ambientSet ? ProviderSupport.enabled(compatibility)
                : ProviderSupport.disabled(compatibility,
                        single ? "No harness/provider/model selected to check against"
                                : "Not available for multiple selection"));

        ContextualActionDescriptor delete = ProviderSupport.descriptor(
                ContextActionIds.AGENT_DELETE, "Delete",
                ContextualActionDescriptor.GROUP_DANGER, ProviderSupport.CTX_AGENT)
                .withConfirmation(ContextualActionDescriptor.Confirmation.DANGEROUS);
        actions.add(single && !isDefault ? ProviderSupport.enabled(delete)
                : ProviderSupport.disabled(delete, isDefault
                        ? "The default agent cannot be deleted"
                        : "Not available for multiple selection"));
        return actions;
    }
}
