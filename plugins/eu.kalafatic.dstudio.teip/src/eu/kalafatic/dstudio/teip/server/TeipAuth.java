package eu.kalafatic.dstudio.teip.server;

import java.util.Optional;

public final class TeipAuth {

    public static final String SYS_TOKEN = "dstudio.teip.auth.token";
    public static final String ENV_TOKEN = "DSTUDIO_TEIP_AUTH_TOKEN";
    public static final String SYS_DEV_MODE = "dstudio.teip.devMode";

    private TeipAuth() {
    }

    public static Optional<String> configuredToken() {
        String token = System.getProperty(SYS_TOKEN);
        if (token == null || token.isBlank()) {
            token = System.getenv(ENV_TOKEN);
        }
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    public static boolean isDevModeExplicitlyEnabled(boolean configurationDevMode) {
        return configurationDevMode || "true".equalsIgnoreCase(System.getProperty(SYS_DEV_MODE));
    }

    public static boolean isLoopback(java.net.InetAddress address) {
        return address != null && address.isLoopbackAddress();
    }
}
