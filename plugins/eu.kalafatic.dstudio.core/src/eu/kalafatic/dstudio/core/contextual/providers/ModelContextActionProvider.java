package eu.kalafatic.dstudio.core.contextual.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionProvider;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;

/**
 * Contextual actions for discovered models. Every action is gated on the owning
 * provider's actual capabilities: load/unload are enabled only when the engine is
 * available, download/remove are disabled with an honest reason because no engine SPI
 * supports them yet.
 */
public class ModelContextActionProvider implements ContextualActionProvider {

    private final InferenceEngineRegistry registry;

    public ModelContextActionProvider(InferenceEngineRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<String> contextTypes() {
        return ProviderSupport.list(ProviderSupport.CTX_MODEL);
    }

    @Override
    public List<ContextualAction> actionsFor(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        String modelId = selection.objectId();
        String engineId = selection.providerId();
        Optional<InferenceEngineAdapter> adapterOpt = registry == null ? Optional.empty()
                : registry.findAdapter(engineId);
        if (modelId == null || modelId.isBlank()) {
            return actions;
        }
        boolean adapterAvailable = adapterOpt.map(InferenceEngineAdapter::isAvailable)
                .orElse(false);
        boolean single = !selection.multiSelect();
        boolean isDefault = "true".equals(selection.state("isDefault"));

        ContextualActionDescriptor setDefault = ProviderSupport.descriptor(
                ContextActionIds.MODEL_SET_DEFAULT, "Set as Default",
                ContextualActionDescriptor.GROUP_SELECTION, ProviderSupport.CTX_MODEL)
                .withDescription("Uses this model for new executions.");
        actions.add(single && !isDefault ? ProviderSupport.enabled(setDefault)
                : ProviderSupport.disabled(setDefault, !single
                        ? "Not available for multiple selection"
                        : "Already the default model"));

        ContextualActionDescriptor load = ProviderSupport.descriptor(
                ContextActionIds.MODEL_LOAD, "Load",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_MODEL);
        ContextualActionDescriptor unload = ProviderSupport.descriptor(
                ContextActionIds.MODEL_UNLOAD, "Unload",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_MODEL);
        if (single && adapterAvailable) {
            actions.add(ProviderSupport.enabled(load));
            actions.add(ProviderSupport.enabled(unload));
        } else {
            String reason = single ? "Provider is not available" : "Not available for "
                    + "multiple selection";
            actions.add(ProviderSupport.disabled(load, reason));
            actions.add(ProviderSupport.disabled(unload, reason));
        }

        ContextualActionDescriptor download = ProviderSupport.descriptor(
                ContextActionIds.MODEL_DOWNLOAD, "Download",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_MODEL);
        actions.add(ProviderSupport.disabled(download,
                "Model download is not supported by this provider"));

        ContextualActionDescriptor remove = ProviderSupport.descriptor(
                ContextActionIds.MODEL_REMOVE, "Remove",
                ContextualActionDescriptor.GROUP_DANGER, ProviderSupport.CTX_MODEL)
                .withConfirmation(ContextualActionDescriptor.Confirmation.DANGEROUS);
        actions.add(ProviderSupport.disabled(remove,
                "Model removal is not supported by this provider"));

        ContextualActionDescriptor refresh = ProviderSupport.descriptor(
                ContextActionIds.MODEL_REFRESH, "Refresh Metadata",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_MODEL);
        actions.add(single && adapterAvailable ? ProviderSupport.enabled(refresh)
                : ProviderSupport.disabled(refresh,
                        single ? "Provider is not available" : "Not available for multiple "
                                + "selection"));

        ContextualActionDescriptor test = ProviderSupport.descriptor(
                ContextActionIds.MODEL_TEST, "Test Inference",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_MODEL)
                .withDescription("Runs a minimal inference request against the model.");
        actions.add(single && adapterAvailable ? ProviderSupport.enabled(test)
                : ProviderSupport.disabled(test,
                        single ? "Provider is not available" : "Not available for multiple "
                                + "selection"));

        ContextualActionDescriptor favorite = ProviderSupport.descriptor(
                ContextActionIds.MODEL_FAVORITE, "Add to Favorites",
                ContextualActionDescriptor.GROUP_SELECTION, ProviderSupport.CTX_MODEL);
        actions.add(ProviderSupport.disabled(favorite,
                "Model favorites are not persisted in this version"));

        ContextualActionDescriptor info = ProviderSupport.descriptor(
                ContextActionIds.MODEL_INFO, "View Model Information",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_MODEL);
        actions.add(single ? ProviderSupport.surfaceGated(info, selection,
                ContextSelection.SURFACE_RCP) : ProviderSupport.disabled(info,
                        "Not available for multiple selection"));

        ContextualActionDescriptor copyInfo = ProviderSupport.descriptor(
                ContextActionIds.MODEL_COPY_INFO, "Copy Model Information",
                ContextualActionDescriptor.GROUP_INSPECTION, ProviderSupport.CTX_MODEL);
        actions.add(single ? ProviderSupport.surfaceGated(copyInfo, selection,
                ContextSelection.SURFACE_RCP) : ProviderSupport.disabled(copyInfo,
                        "Not available for multiple selection"));
        return actions;
    }
}
