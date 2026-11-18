package eu.kalafatic.dstudio.application;

import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleStage;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class DStudioServerApplication implements IApplication {

    private DStudioHeadlessRunner runner;

    @Override
    public Object start(IApplicationContext context) {
        // Register the local update repository (<install>/updates) when present.
        DStudioUpdateRepositoryRegistrar.registerAsync();
        DStudioLifecycleCoordinator coordinator = new DStudioLifecycleCoordinator();
        DStudioRuntime.setCoordinator(coordinator);
        coordinator.advance(DStudioLifecycleStage.CORE_SERVICE_INITIALIZATION);
        runner = new DStudioHeadlessRunner(coordinator);
        return runner.run(context);
    }

    @Override
    public void stop() {
        if (runner != null) {
            runner.stop();
        }
    }
}
