package eu.kalafatic.dstudio.application;

import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;

public final class DStudioRuntime {

    private static volatile DStudioLifecycleCoordinator coordinator;

    private DStudioRuntime() {
    }

    public static void setCoordinator(DStudioLifecycleCoordinator newCoordinator) {
        coordinator = newCoordinator;
    }

    public static DStudioLifecycleCoordinator getCoordinator() {
        return coordinator;
    }
}
