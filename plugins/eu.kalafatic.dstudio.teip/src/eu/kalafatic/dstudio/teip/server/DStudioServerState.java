package eu.kalafatic.dstudio.teip.server;

/**
 * Web server lifecycle state. {@link #DISABLED} is reported by the lifecycle
 * coordinator when {@code webServer.enabled=false}; the server itself reports
 * STOPPED / STARTING / RUNNING / STOPPING / FAILED.
 */
public enum DStudioServerState {
    DISABLED,
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    FAILED
}
