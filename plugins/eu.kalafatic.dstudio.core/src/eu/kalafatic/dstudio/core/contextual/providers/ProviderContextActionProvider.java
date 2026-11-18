package eu.kalafatic.dstudio.core.contextual.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionProvider;
import eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;

/**
 * Contextual actions for the inference-provider dimension (ENR, Ollama, OpenAI-compatible,
 * Anthropic, …). ENR remains fully independent from Ollama — every engine keeps its own
 * identity, configuration and lifecycle through {@link InferenceEngineRegistry}.
 */
public class ProviderContextActionProvider implements ContextualActionProvider {

    private final InferenceEngineRegistry registry;

    public ProviderContextActionProvider(InferenceEngineRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<String> contextTypes() {
        return ProviderSupport.list(ProviderSupport.CTX_PROVIDER,
                ProviderSupport.CTX_PROVIDER_REGISTRY);
    }

    @Override
    public List<ContextualAction> actionsFor(ContextSelection selection) {
        if (ProviderSupport.CTX_PROVIDER_REGISTRY.equals(selection.objectType())) {
            return registryActions();
        }
        return providerActions(selection);
    }

    private List<ContextualAction> registryActions() {
        List<ContextualAction> actions = new ArrayList<>();
        boolean hasEngines = registry != null && !registry.listEngines().isEmpty();

        ContextualActionDescriptor scan = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_REGISTRY_SCAN, "Scan Environment",
                ContextualActionDescriptor.GROUP_SELECTION,
                ProviderSupport.CTX_PROVIDER_REGISTRY)
                .withDescription("Tests the connection of every registered provider and "
                        + "updates its status.");
        actions.add(hasEngines ? ProviderSupport.enabled(scan)
                : ProviderSupport.disabled(scan, "No providers registered"));

        ContextualActionDescriptor refresh = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_REGISTRY_REFRESH, "Refresh",
                ContextualActionDescriptor.GROUP_INSPECTION,
                ProviderSupport.CTX_PROVIDER_REGISTRY);
        actions.add(ProviderSupport.enabled(refresh));

        // Runtime provider instances can be registered through the engine factory.
        ContextualActionDescriptor add = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_REGISTRY_ADD, "Add Provider",
                ContextualActionDescriptor.GROUP_CONFIGURATION,
                ProviderSupport.CTX_PROVIDER_REGISTRY)
                .withDescription("Registers a new inference provider instance.");
        actions.add(ProviderSupport.enabled(add));
        return actions;
    }

    private List<ContextualAction> providerActions(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        String engineId = selection.objectId();
        Optional<InferenceEngineDescriptor> descriptorOpt = registry == null
                ? Optional.empty() : registry.findEngine(engineId);
        if (descriptorOpt.isEmpty()) {
            return actions;
        }
        InferenceEngineDescriptor descriptor = descriptorOpt.get();
        boolean available = registry.findAdapter(engineId)
                .map(a -> a.isAvailable()).orElse(false);
        boolean isDefault = registry.getSelectedDefaultEngineId()
                .map(engineId::equals).orElse(false);
        boolean single = !selection.multiSelect();

        ContextualActionDescriptor setDefault = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_SET_DEFAULT, "Set as Default",
                ContextualActionDescriptor.GROUP_SELECTION, ProviderSupport.CTX_PROVIDER);
        actions.add(single && available && !isDefault ? ProviderSupport.enabled(setDefault)
                : ProviderSupport.disabled(setDefault, !single
                        ? "Not available for multiple selection"
                        : isDefault ? "Already the default provider"
                                : "Provider is not available"));

        ContextualActionDescriptor edit = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_EDIT, "Edit Configuration",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_PROVIDER);
        actions.add(single ? ProviderSupport.surfaceGated(edit, selection,
                ContextSelection.SURFACE_RCP)
                : ProviderSupport.disabled(edit, "Not available for multiple selection"));

        ContextualActionDescriptor configure = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_CONFIGURE, "Configure",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_PROVIDER);
        actions.add(single ? ProviderSupport.surfaceGated(configure, selection,
                ContextSelection.SURFACE_WEB)
                : ProviderSupport.disabled(configure, "Not available for multiple selection"));

        ContextualActionDescriptor test = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_TEST, "Test Connection",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_PROVIDER);
        actions.add(single ? ProviderSupport.enabled(test)
                : ProviderSupport.disabled(test, "Not available for multiple selection"));

        ContextualActionDescriptor enable = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_ENABLE, "Enable",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_PROVIDER);
        ContextualActionDescriptor disable = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_DISABLE, "Disable",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_PROVIDER);
        if (single) {
            actions.add(descriptor.isEnabled() ? ProviderSupport.disabled(enable,
                    "Provider is already enabled") : ProviderSupport.enabled(enable));
            actions.add(descriptor.isEnabled() ? ProviderSupport.enabled(disable)
                    : ProviderSupport.disabled(disable, "Provider is already disabled"));
        }

        ContextualActionDescriptor discover = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_DISCOVER_MODELS, "Discover Models",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_PROVIDER);
        actions.add(single && available ? ProviderSupport.enabled(discover)
                : ProviderSupport.disabled(discover,
                        single ? "Provider is not available" : "Not available for multiple "
                                + "selection"));

        ContextualActionDescriptor endpoint = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_VIEW_ENDPOINT, "View Endpoint",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_PROVIDER);
        actions.add(single ? ProviderSupport.enabled(endpoint)
                : ProviderSupport.disabled(endpoint, "Not available for multiple selection"));

        ContextualActionDescriptor logs = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_VIEW_LOGS, "View Logs",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_PROVIDER);
        actions.add(ProviderSupport.disabled(logs, "This provider does not expose logs"));

        ContextualActionDescriptor remove = ProviderSupport.descriptor(
                ContextActionIds.PROVIDER_REMOVE, "Remove",
                ContextualActionDescriptor.GROUP_DANGER, ProviderSupport.CTX_PROVIDER)
                .withConfirmation(ContextualActionDescriptor.Confirmation.DANGEROUS);
        actions.add(single && !isDefault ? ProviderSupport.enabled(remove)
                : ProviderSupport.disabled(remove, isDefault
                        ? "The default provider cannot be removed"
                        : "Not available for multiple selection"));
        return actions;
    }
}
