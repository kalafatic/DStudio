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
import eu.kalafatic.dstudio.core.service.ProjectService;
import eu.kalafatic.dstudio.core.model.Project;

/**
 * Contextual actions for projects. Only operations the DStudio domain actually owns
 * are contributed; build/test/git actions belong to other family products and are not
 * listed at all (they are never shown as executable here).
 */
public class ProjectContextActionProvider implements ContextualActionProvider {

    private final ProjectService projectService;
    private final HarnessRegistry harnessRegistry;

    public ProjectContextActionProvider(ProjectService projectService,
            HarnessRegistry harnessRegistry) {
        this.projectService = projectService;
        this.harnessRegistry = harnessRegistry;
    }

    @Override
    public List<String> contextTypes() {
        return ProviderSupport.list(ProviderSupport.CTX_PROJECT, ProviderSupport.CTX_REPOSITORY);
    }

    @Override
    public List<ContextualAction> actionsFor(ContextSelection selection) {
        List<ContextualAction> actions = new ArrayList<>();
        String projectId = selection.objectId();
        if (projectId == null || projectId.isBlank()) {
            return actions;
        }
        Optional<Project> projectOpt = projectService == null ? Optional.empty()
                : projectService.findProject(projectId);
        if (projectOpt.isEmpty()) {
            return actions;
        }
        boolean single = !selection.multiSelect();

        ContextualActionDescriptor open = ProviderSupport.descriptor(
                ContextActionIds.PROJECT_OPEN, "Open",
                ContextualActionDescriptor.GROUP_SELECTION, ProviderSupport.CTX_PROJECT);
        actions.add(single ? ProviderSupport.surfaceGated(open, selection,
                ContextSelection.SURFACE_RCP) : ProviderSupport.disabled(open,
                        "Not available for multiple selection"));

        ContextualActionDescriptor createSession = ProviderSupport.descriptor(
                ContextActionIds.PROJECT_CREATE_SESSION, "Create Session",
                ContextualActionDescriptor.GROUP_LIFECYCLE, ProviderSupport.CTX_PROJECT)
                .withDescription("Starts a coding execution on this project.");
        boolean harnessSelected = harnessRegistry != null
                && harnessRegistry.findSelectedDefaultAdapter().isPresent();
        actions.add(single && harnessSelected ? ProviderSupport.enabled(createSession)
                : ProviderSupport.disabled(createSession,
                        single ? "No harness selected" : "Not available for multiple "
                                + "selection"));

        ContextualActionDescriptor close = ProviderSupport.descriptor(
                ContextActionIds.PROJECT_CLOSE, "Close Project",
                ContextualActionDescriptor.GROUP_DANGER, ProviderSupport.CTX_PROJECT)
                .withConfirmation(ContextualActionDescriptor.Confirmation.CONFIRM);
        actions.add(single ? ProviderSupport.enabled(close)
                : ProviderSupport.disabled(close, "Not available for multiple selection"));
        return actions;
    }
}
