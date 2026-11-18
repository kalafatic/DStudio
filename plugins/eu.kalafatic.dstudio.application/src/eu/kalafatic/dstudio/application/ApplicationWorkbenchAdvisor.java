package eu.kalafatic.dstudio.application;

import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleStage;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;

public class ApplicationWorkbenchAdvisor extends WorkbenchAdvisor {

    private final DStudioLifecycleCoordinator coordinator;

    public ApplicationWorkbenchAdvisor(DStudioLifecycleCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public String getInitialWindowPerspectiveId() {
        return "eu.kalafatic.dstudio.ui.perspective.development";
    }

    @Override
    public void initialize(IWorkbenchConfigurer configurer) {
        configurer.setSaveAndRestore(true);
    }

    @Override
    public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(
            IWorkbenchWindowConfigurer configurer) {
        return new ApplicationWorkbenchWindowAdvisor(configurer);
    }

    @Override
    public void postStartup() {
        coordinator.advance(DStudioLifecycleStage.WORKBENCH_RUNNING);
    }

    @Override
    public boolean preShutdown() {
        coordinator.advance(DStudioLifecycleStage.APPLICATION_SHUTDOWN);
        return true;
    }

    @Override
    public void postShutdown() {
        coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
    }
}
