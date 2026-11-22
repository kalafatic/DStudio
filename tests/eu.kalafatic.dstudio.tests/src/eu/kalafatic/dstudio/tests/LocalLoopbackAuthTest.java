package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import eu.kalafatic.dstudio.teip.server.DStudioServer;
import eu.kalafatic.dstudio.teip.server.TeipAuth;

/**
 * Current-phase auth policy: loopback clients are always local-trusted. The
 * embedded SWT Browser, the desktop application, CLI tools and local integrations
 * must reach every endpoint without a token, without dev mode and without any
 * session/login mechanism — in PDE Run, PDE Debug and packaged RCP products alike.
 * Remote (non-loopback) clients keep the optional bearer-token gate unchanged.
 */
class LocalLoopbackAuthTest {

    private HttpResponse<String> get(int port, String path, String token)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void loopbackApiWorksWithoutTokenOrDevMode() throws Exception {
        if (System.getenv("DSTUDIO_TEIP_AUTH_TOKEN") != null) {
            return; // external token configured: not the local no-auth scenario
        }
        String previous = System.getProperty(TeipAuth.SYS_TOKEN);
        System.clearProperty(TeipAuth.SYS_TOKEN);
        DStudioServer server = null;
        try {
            assertTrue(TeipAuth.configuredToken().isEmpty(),
                    "precondition: no token configured");
            // dev mode explicitly OFF: local usage must not depend on it.
            server = new DStudioServer("127.0.0.1", 0, null, null, null, null,
                    exchange -> exchange.close(), false, null, null, null,
                    null, null, null);
            server.start();
            int port = server.getBoundPort();

            HttpResponse<String> status = get(port, "/api/status", null);
            assertEquals(200, status.statusCode(),
                    "loopback /api/status must not require authentication");
            assertTrue(status.body().contains("frontendVersion"),
                    "status payload must expose the frontend revision");

            assertEquals(200, get(port, "/api/diagnostics", null).statusCode(),
                    "loopback diagnostics must not require authentication");
        } finally {
            if (server != null) {
                server.stop();
            }
            if (previous != null) {
                System.setProperty(TeipAuth.SYS_TOKEN, previous);
            } else {
                System.clearProperty(TeipAuth.SYS_TOKEN);
            }
        }
    }

    @Test
    void loopbackStaysOpenEvenWhenATokenIsConfigured() throws Exception {
        if (System.getenv("DSTUDIO_TEIP_AUTH_TOKEN") != null) {
            return;
        }
        String previous = System.getProperty(TeipAuth.SYS_TOKEN);
        System.setProperty(TeipAuth.SYS_TOKEN, "remote-only-token");
        DStudioServer server = null;
        try {
            server = new DStudioServer("127.0.0.1", 0, null, null, null, null,
                    exchange -> exchange.close(), false, null, null, null,
                    null, null, null);
            server.start();
            int port = server.getBoundPort();
            assertEquals(200, get(port, "/api/status", null).statusCode(),
                    "the local workflow must not be gated by a remote token");
        } finally {
            if (server != null) {
                server.stop();
            }
            if (previous != null) {
                System.setProperty(TeipAuth.SYS_TOKEN, previous);
            } else {
                System.clearProperty(TeipAuth.SYS_TOKEN);
            }
        }
    }
}
