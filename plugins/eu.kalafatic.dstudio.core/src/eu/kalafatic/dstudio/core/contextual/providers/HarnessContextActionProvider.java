package eu.kalafatic.dstudio.core.contextual.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionProvider;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;

/**
 * Contextual actions for the coding-harness dimension. Availability is computed from
 * the live {@link HarnessRegistry}: selection, enablement, default, capabilities and
 * connection status. Lifecycle actions (start/stop/restart) are capability-gated —
 * a harness that does not advertise {@code lifecycle:start/stop} shows them disabled
 * with an explanation instead of pretending they work.
 */
public class HarnessContextActionProvider implements ContextualActionProvider {

    private final HarnessRegistry registry;

    public HarnessContextActionProvider(HarnessRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<String> contextTypes() {
        return ProviderSupport.list(ProviderSupport.CTX_HARNESS,
                ProviderSupport.CTX_HARNESS_REGISTRY);
    }

    @Override
    public List<ContextualAction> actionsFor(ContextSelection selection) {
        if (ProviderSupport.CTX_HARNESS_REGISTRY.equals(selection.objectType())) {
            return registryActions(selection);
        }
        return harnessActions(selection);
    }

    private List<ContextualAction> registryActions(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        boolean hasHarnesses = registry != null && !registry.listHarnesses().isEmpty();

        ContextualActionDescriptor scan = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_REGISTRY_SCAN, "Scan Environment",
                ContextualActionDescriptor.GROUP_SELECTION,
                ProviderSupport.CTX_HARNESS_REGISTRY)
                .withDescription("Tests the connection of every registered harness and "
                        + "updates their status.");
        actions.add(hasHarnesses ? ProviderSupport.enabled(scan)
                : ProviderSupport.disabled(scan, "No harnesses registered"));

        ContextualActionDescriptor refresh = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_REGISTRY_REFRESH, "Refresh",
                ContextualActionDescriptor.GROUP_INSPECTION,
                ProviderSupport.CTX_HARNESS_REGISTRY);
        actions.add(ProviderSupport.enabled(refresh));

        ContextualActionDescriptor add = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_REGISTRY_ADD, "Add Harness",
                ContextualActionDescriptor.GROUP_CONFIGURATION,
                ProviderSupport.CTX_HARNESS_REGISTRY);
        actions.add(ProviderSupport.disabled(add,
                "Harness adapters are compiled into the application"));
        return actions;
    }

    private List<ContextualAction> harnessActions(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        String harnessId = selection.objectId();
        Optional<HarnessDescriptor> descriptorOpt = registry == null ? Optional.empty()
                : registry.findHarness(harnessId);
        if (descriptorOpt.isEmpty()) {
            return actions;
        }
        HarnessDescriptor descriptor = descriptorOpt.get();
        boolean available = registry.findAdapter(harnessId)
                .map(a -> a.isAvailable()).orElse(false);
        boolean isDefault = registry.getSelectedDefaultHarnessId()
                .map(harnessId::equals).orElse(false);
        boolean single = !selection.multiSelect();

        ContextualActionDescriptor setDefault = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_SET_DEFAULT, "Set as Default",
                ContextualActionDescriptor.GROUP_SELECTION, ProviderSupport.CTX_HARNESS)
                .withDescription("Uses this harness for new coding executions.");
        actions.add(single && available && !isDefault
                ? ProviderSupport.enabled(setDefault)
                : ProviderSupport.disabled(setDefault, !single
                        ? "Not available for multiple selection"
                        : isDefault ? "Already the default harness"
                                : "Harness is not available"));

        ContextualActionDescriptor edit = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_EDIT, "Edit Configuration",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_HARNESS);
        actions.add(single ? ProviderSupport.surfaceGated(edit, selection,
                ContextSelection.SURFACE_RCP)
                : ProviderSupport.disabled(edit, "Not available for multiple selection"));

        ContextualActionDescriptor configure = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_CONFIGURE, "Configure",
                ContextualActionDescriptor.GROUP_CONFIGURATION, ProviderSupport.CTX_HARNESS)
                .withDescription("Updates the harness connection configuration.");
        actions.add(single ? ProviderSupport.surfaceGated(configure, selection,
                ContextSelection.SURFACE_WEB)
                : ProviderSupport.disabled(configure, "Not available for multiple selection"));

        ContextualActionDescriptor test = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_TEST, "Test Connection",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_HARNESS);
        actions.add(single ? ProviderSupport.enabled(test)
                : ProviderSupport.disabled(test, "Not available for multiple selection"));

        ContextualActionDescriptor enable = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_ENABLE, "Enable",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_HARNESS);
        ContextualActionDescriptor disable = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_DISABLE, "Disable",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_HARNESS);
        if (single) {
            actions.add(descriptor.isEnabled() ? ProviderSupport.disabled(enable,
                    "Harness is already enabled") : ProviderSupport.enabled(enable));
            actions.add(descriptor.isEnabled() ? ProviderSupport.enabled(disable)
                    : ProviderSupport.disabled(disable, "Harness is already disabled"));
        }

        // Capability-gated lifecycle actions.
        ContextualActionDescriptor start = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_START, "Start",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_HARNESS);
        ContextualActionDescriptor stop = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_STOP, "Stop",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_HARNESS);
        ContextualActionDescriptor restart = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_RESTART, "Restart",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_HARNESS);
        boolean canStart = ProviderSupport.hasCapability(selection,
                ProviderSupport.CAP_LIFECYCLE_START);
        boolean canStop = ProviderSupport.hasCapability(selection,
                ProviderSupport.CAP_LIFECYCLE_STOP);
        String lifecycleReason = "This harness does not expose service lifecycle control";
        actions.add(canStart ? ProviderSupport.enabled(start)
                : ProviderSupport.disabled(start, lifecycleReason));
        actions.add(canStop ? ProviderSupport.enabled(stop)
                : ProviderSupport.disabled(stop, lifecycleReason));
        actions.add((canStart && canStop) ? ProviderSupport.enabled(restart)
                : ProviderSupport.disabled(restart, lifecycleReason));

        ContextualActionDescriptor capabilities = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_VIEW_CAPABILITIES, "View Capabilities",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_HARNESS);
        actions.add(ProviderSupport.surfaceGated(capabilities, selection,
                ContextSelection.SURFACE_RCP));

        ContextualActionDescriptor logs = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_VIEW_LOGS, "View Logs",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_HARNESS);
        actions.add(ProviderSupport.hasCapability(selection, ProviderSupport.CAP_LOGS)
                ? ProviderSupport.disabled(logs, "Log viewer not available for this harness")
                : ProviderSupport.disabled(logs, "This harness does not expose logs"));

        ContextualActionDescriptor remove = ProviderSupport.descriptor(
                ContextActionIds.HARNESS_REMOVE, "Remove Registration",
                ContextualActionDescriptor.GROUP_DANGER, ProviderSupport.CTX_HARNESS)
                .withConfirmation(ContextualActionDescriptor.Confirmation.DANGEROUS);
        actions.add(single && !isDefault ? ProviderSupport.enabled(remove)
                : ProviderSupport.disabled(remove, isDefault
                        ? "The default harness cannot be removed" : "Not available for "
                                + "multiple selection"));
        return actions;
    }
}
