package eu.kalafatic.dstudio.teip.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Serves the embedded Web IDE assets from the bundle's {@code /web/} classpath
 * resources (Phase 7, S6). No second server: the existing backend serves the frontend,
 * so desktop and browser clients stay on one authoritative core.
 */
public class WebAssetsHandler implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "svg", "image/svg+xml",
            "ico", "image/x-icon");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/".equals(path)) {
            path = "/index.html";
        }
        if (path.startsWith("/web")) {
            path = path.substring("/web".length());
            if (path.isEmpty() || "/".equals(path)) {
                path = "/index.html";
            }
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        String resource = "web/" + path;
        InputStream in = openBundleResource(resource);
        if (in == null) {
            byte[] notFound = "Not found".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, notFound.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(notFound);
            }
            exchange.close();
            return;
        }
        byte[] payload;
        try (in) {
            payload = in.readAllBytes();
        }
        String extension = resource.contains(".")
                ? resource.substring(resource.lastIndexOf('.') + 1).toLowerCase() : "";
        exchange.getResponseHeaders().set("Content-Type",
                CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
        exchange.close();
    }

    /**
     * Single resolution mechanism for every execution mode: the OSGi bundle entry
     * API. In packaged products the bundle is a jar ({@code web/…} entries); in a
     * PDE workspace launch the bundle is directory-installed at the project
     * location, where {@code getEntry} exposes the same logical {@code web/…}
     * paths from the authoritative source tree. One source, one mechanism, no
     * output-folder or builder dependency.
     */
    private InputStream openBundleResource(String resource) {
        try {
            org.osgi.framework.Bundle bundle = org.osgi.framework.FrameworkUtil
                    .getBundle(WebAssetsHandler.class);
            if (bundle == null) {
                return null;
            }
            java.net.URL url = bundle.getEntry(resource);
            return url == null ? null : url.openStream();
        } catch (Exception e) {
            return null;
        }
    }
}
