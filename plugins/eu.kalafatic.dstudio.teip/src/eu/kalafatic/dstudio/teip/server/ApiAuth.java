package eu.kalafatic.dstudio.teip.server;

import com.sun.net.httpserver.HttpExchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Authentication for the DStudio management API surface ({@code /api/*}). Uses the same
 * bearer token as the TEIP surface ({@code dstudio.teip.auth.token} /
 * {@code DSTUDIO_TEIP_AUTH_TOKEN}) with the same explicit dev-mode loopback opt-out, so
 * authorization is consistent across every HTTP endpoint of the backend.
 */
public final class ApiAuth {

    public static final String ERROR_CODE = "AUTHENTICATION_FAILED";

    private ApiAuth() {
    }

    /** Throws {@link ApiAuthException} unless the request is authorized. */
    public static void authenticate(HttpExchange exchange, boolean devModeConfigured) {
        Optional<String> token = TeipAuth.configuredToken();
        if (token.isPresent()) {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new ApiAuthException("Missing Authorization: Bearer <token> header");
            }
            String presented = header.substring("Bearer ".length()).trim();
            if (!constantTimeEquals(token.get(), presented)) {
                throw new ApiAuthException("Invalid bearer token");
            }
            return;
        }
        if (TeipAuth.isDevModeExplicitlyEnabled(devModeConfigured)
                && TeipAuth.isLoopback(exchange.getRemoteAddress().getAddress())) {
            return;
        }
        throw new ApiAuthException("API authentication required: configure "
                + "dstudio.teip.auth.token or enable explicit dev mode "
                + "(dstudio.teip.devMode=true) for loopback-only access");
    }

    private static boolean constantTimeEquals(String expected, String presented) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    public static class ApiAuthException extends RuntimeException {
        public ApiAuthException(String message) {
            super(message);
        }
    }
}
