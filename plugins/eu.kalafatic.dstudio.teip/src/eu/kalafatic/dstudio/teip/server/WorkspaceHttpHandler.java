package eu.kalafatic.dstudio.teip.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.service.ProjectService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Workspace/file API for the Web IDE (Phase 7, S6): project open/close/list plus
 * contained file browsing, reading and saving. All paths are resolved against the
 * project root with strict containment (no path traversal). Authentication is the
 * shared API auth.
 */
public class WorkspaceHttpHandler implements HttpHandler {

    public static final String PATH = "/api/workspace";
    static final long MAX_FILE_BYTES = 10 * 1024 * 1024;

    private final ProjectService projectService;
    private final boolean devModeConfigured;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkspaceHttpHandler(ProjectService projectService, boolean devModeConfigured) {
        this.projectService = projectService;
        this.devModeConfigured = devModeConfigured;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiAuth.authenticate(exchange, devModeConfigured);
        } catch (ApiAuth.ApiAuthException e) {
            respond(exchange, 401, error("AUTHENTICATION_FAILED", e.getMessage()));
            return;
        }
        try {
            String method = exchange.getRequestMethod();
            String rest = exchange.getRequestURI().getPath().substring(PATH.length());
            if (rest.equals("/projects") && "GET".equals(method)) {
                List<Map<String, Object>> projects = new ArrayList<>();
                if (projectService != null) {
                    for (Project project : projectService.listProjects()) {
                        projects.add(Map.of("id", project.id(), "name", project.name(),
                                "rootPath", project.rootPath()));
                    }
                }
                respond(exchange, 200, Map.of("projects", projects));
                return;
            }
            if (rest.equals("/projects") && "POST".equals(method)) {
                if (projectService == null) {
                    respond(exchange, 503, error("UNAVAILABLE",
                            "Project service is not available"));
                    return;
                }
                String rootPath = mapper.readTree(exchange.getRequestBody())
                        .path("path").asText("");
                Project project = projectService.openProject(rootPath);
                respond(exchange, 201, Map.of("id", project.id(), "name", project.name(),
                        "rootPath", project.rootPath()));
                return;
            }
            if (rest.startsWith("/projects/") && "POST".equals(method)
                    && rest.endsWith("/close")) {
                String projectId = rest.substring("/projects/".length(),
                        rest.length() - "/close".length());
                projectService.closeProject(projectId);
                respond(exchange, 200, Map.of("id", projectId, "closed", true));
                return;
            }
            if (rest.equals("/files")) {
                handleFiles(exchange, method);
                return;
            }
            respond(exchange, 404, error("NOT_FOUND", "Unknown workspace route: " + rest));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, error("VALIDATION_FAILED", e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, error("INTERNAL_ERROR", e.getClass().getSimpleName()
                    + ": " + e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void handleFiles(HttpExchange exchange, String method) throws IOException {
        String projectId = query(exchange, "project");
        String relative = query(exchange, "path");
        if (projectId == null || projectId.isBlank()) {
            respond(exchange, 400, error("VALIDATION_FAILED", "project parameter is required"));
            return;
        }
        Optional<Project> project = projectService.findProject(projectId);
        if (project.isEmpty()) {
            respond(exchange, 404, error("NOT_FOUND", "Unknown project: " + projectId));
            return;
        }
        Path root = Path.of(project.get().rootPath()).normalize();
        Path target = root.resolve(relative == null ? "" : relative).normalize();
        if (!target.startsWith(root)) {
            respond(exchange, 400, error("VALIDATION_FAILED",
                    "Path escapes the project root"));
            return;
        }
        if ("PUT".equals(method)) {
            if (Files.isDirectory(target)) {
                respond(exchange, 400, error("VALIDATION_FAILED",
                        "Cannot write a directory"));
                return;
            }
            String content = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
            respond(exchange, 200, Map.of("project", projectId, "path", relative,
                    "saved", true));
            return;
        }
        if (!Files.exists(target)) {
            respond(exchange, 404, error("NOT_FOUND", "Path does not exist: " + relative));
            return;
        }
        if ("GET".equals(method)) {
            if (Files.isDirectory(target)) {
                List<Map<String, Object>> children = new ArrayList<>();
                try (var stream = Files.list(target)) {
                    for (Path child : stream.sorted().toList()) {
                        children.add(Map.of("name", child.getFileName().toString(),
                                "path", root.relativize(child).toString(),
                                "directory", Files.isDirectory(child)));
                    }
                }
                respond(exchange, 200, Map.of("project", projectId, "path",
                        relative == null ? "" : relative, "directory", true,
                        "children", children));
            } else if (Files.size(target) > MAX_FILE_BYTES) {
                respond(exchange, 413, error("TOO_LARGE",
                        "File exceeds the " + MAX_FILE_BYTES + " byte web limit"));
            } else {
                String content = Files.readString(target);
                respond(exchange, 200, Map.of("project", projectId, "path", relative,
                        "directory", false, "content", content));
            }
            return;
        }
        respond(exchange, 405, error("METHOD_NOT_ALLOWED", "Use GET or PUT"));
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(name)) {
                return URLDecoder.decode(pair.substring(equals + 1),
                        StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> body)
            throws IOException {
        byte[] payload = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", code);
        envelope.put("message", message == null ? "" : message);
        return envelope;
    }
}
