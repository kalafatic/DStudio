package eu.kalafatic.dstudio.ui;

import eu.kalafatic.dstudio.application.DStudioRuntime;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import eu.kalafatic.dstudio.core.event.EventBus;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.core.service.ProjectService;
import eu.kalafatic.dstudio.core.service.ReviewService;
import eu.kalafatic.dstudio.core.service.WorkspaceDiffService;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class DStudioUiPlugin extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "eu.kalafatic.dstudio.ui";

    private static DStudioUiPlugin instance;

    public DStudioUiPlugin() {
        instance = this;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        instance = null;
        super.stop(context);
    }

    public static DStudioUiPlugin getInstance() {
        return instance;
    }

    public DStudioLifecycleCoordinator coordinator() {
        return DStudioRuntime.getCoordinator();
    }

    public HarnessRegistry harnessRegistry() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getHarnessRegistry();
    }

    public InferenceEngineRegistry inferenceEngineRegistry() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getInferenceEngineRegistry();
    }

    public eu.kalafatic.dstudio.core.inference.InferenceService inferenceService() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getInferenceService();
    }

    public ExecutionService executionService() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getExecutionService();
    }

    public ProjectService projectService() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getProjectService();
    }

    public ReviewService reviewService() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getReviewService();
    }

    public eu.kalafatic.dstudio.core.agent.AgentProfileRegistry agentProfileRegistry() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getAgentProfileRegistry();
    }

    public eu.kalafatic.dstudio.core.config.ExecutionConfigurationService
            executionConfigurationService() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getExecutionConfigurationService();
    }

    public WorkspaceDiffService diffService() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getDiffService();
    }

    public eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry
            contextualActionRegistry() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getContextualActionRegistry();
    }

    public EventBus eventBus() {
        DStudioLifecycleCoordinator coordinator = coordinator();
        return coordinator == null ? null : coordinator.getEventBus();
    }
}
