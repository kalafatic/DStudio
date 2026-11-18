package eu.kalafatic.dstudio.core.config;

import java.nio.file.Path;

public record DStudioConfiguration(String appName, String appVersion, Path configurationDirectory,
        String teipBindAddress, int teipPort, String teipNetworkMode, int diagnosticsPort,
        boolean teipDevMode, String defaultHarnessId, String defaultModel,
        long executionTimeoutSeconds, String evoMcpEndpoint, String evoMcpToken) {

    public static final String APP_NAME = "DStudio - Darwin Studio";
    public static final String APP_VERSION = "2.6.11";
    /**
     * Family port convention (EVO 28xxx, TRYST 38xxx, DCode 48xxx, DStudio 58xxx):
     * primary TEIP port 58080, diagnostics 58180. DCode/DCoder owns the 48xxx block
     * (48080 default) and must never occupy 58xxx.
     *
     * <p>Legacy values (TEIP 48080 / diagnostics 48180) are migrated on load by
     * {@code DStudioConfigurationServiceImpl}.</p>
     */
    /** Family port convention (documented in AGENTS.md): DStudio 48xxx, DCode 58xxx. */
    public static final int DEFAULT_TEIP_PORT = 48080;
    public static final int DEFAULT_DIAGNOSTICS_PORT = 48180;
    /** Transient 58xxx values (briefly shipped, colliding with DCode's 58080). */
    public static final int LEGACY_TEIP_PORT = 48080;
    public static final int LEGACY_DIAGNOSTICS_PORT = 48180;
    public static final int TRANSIENT_TEIP_PORT = 58080;
    public static final int TRANSIENT_DIAGNOSTICS_PORT = 58180;
    public static final String NETWORK_LOOPBACK = "LOOPBACK";
    public static final String NETWORK_LAN = "LAN";
    public static final String NETWORK_ALL = "ALL_INTERFACES";
    public static final long DEFAULT_EXECUTION_TIMEOUT_SECONDS = 1800;

    public static DStudioConfiguration defaults(Path configurationDirectory) {
        return new DStudioConfiguration(APP_NAME, APP_VERSION, configurationDirectory,
                "127.0.0.1", DEFAULT_TEIP_PORT, NETWORK_LOOPBACK, DEFAULT_DIAGNOSTICS_PORT,
                false, "", "", DEFAULT_EXECUTION_TIMEOUT_SECONDS, "", "");
    }
}
