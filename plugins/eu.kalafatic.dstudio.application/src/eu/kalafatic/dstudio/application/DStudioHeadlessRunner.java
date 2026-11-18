package eu.kalafatic.dstudio.application;

import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleStage;
import eu.kalafatic.dstudio.teip.server.PortConflictException;
import java.util.concurrent.CountDownLatch;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class DStudioHeadlessRunner {

    private final DStudioLifecycleCoordinator coordinator;
    private final CountDownLatch stopLatch = new CountDownLatch(1);
    private volatile Thread shutdownHook;

    public DStudioHeadlessRunner(DStudioLifecycleCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    public Object run(IApplicationContext context) {
        try {
            coordinator.advance(DStudioLifecycleStage.APPLICATION_READY);
        } catch (PortConflictException e) {
            System.err.println("[DStudio] STARTUP FAILED: " + e.getMessage());
            System.err.println("[DStudio] The DStudio server (TEIP/API/events) could not "
                    + "bind " + e.bindAddress() + ":" + e.port() + ".");
            System.err.println("[DStudio] What to do:");
            System.err.println("[DStudio]   1. Stop the process occupying the port "
                    + "(e.g. another DStudio instance or a family product), or");
            System.err.println("[DStudio]   2. Set teip.port=NNNN in "
                    + coordinator.getPaths().getPropertiesFile() + " or start with "
                    + "-Ddstudio.teip.port=NNNN, then restart.");
            System.err.println("[DStudio] Your existing configuration is preserved; "
                    + "nothing was overwritten.");
            coordinator.terminateAfterStartupFailure();
            DStudioStartupExit.terminate(1);
            return Integer.valueOf(1);
        } catch (RuntimeException e) {
            System.err.println("[DStudio] STARTUP FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            coordinator.terminateAfterStartupFailure();
            DStudioStartupExit.terminate(1);
            return Integer.valueOf(1);
        }
        Thread hook = new Thread(() -> {
            coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
        }, "dstudio-headless-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);
        shutdownHook = hook;
        System.out.println("[DStudio] Headless runtime ready (config dir: "
                + coordinator.getPaths().getConfigurationDirectory() + ")");
        try {
            stopLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return IApplication.EXIT_OK;
    }

    public void stop() {
        coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
        stopLatch.countDown();
    }
}
