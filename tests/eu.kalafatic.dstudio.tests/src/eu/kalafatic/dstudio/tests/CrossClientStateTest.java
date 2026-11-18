package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.kalafatic.dstudio.application.DStudioInferenceEngineFactory;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleCoordinator;
import eu.kalafatic.dstudio.application.lifecycle.DStudioLifecycleStage;
import eu.kalafatic.dstudio.infrastructure.config.DStudioPaths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7 (S7): cross-client shared state on the one authoritative backend. Whatever
 * one client does (Web IDE over HTTP / TEIP consumer) must be immediately visible to
 * every other client (RCP core services, diagnostics, registries) and vice versa —
 * no duplicate state, no second backend.
 */
class CrossClientStateTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void webSubmittedTaskVisibleInRcpCoreAndDiagnostics() throws Exception {
        DStudioPaths paths = new DStudioPaths(
                Files.createTempDirectory("dstudio-cross").resolve("home"));
        Files.createDirectories(paths.getConfigurationDirectory());
        Files.writeString(paths.getPropertiesFile(), "teip.devMode=true\nteip.port=0\n");
        DStudioLifecycleCoordinator coordinator = new DStudioLifecycleCoordinator(paths);
        try {
            coordinator.advance(DStudioLifecycleStage.APPLICATION_READY);
            // the "RCP client" adds a harness — the "web client" must be able to use it
            coordinator.getHarnessRegistry()
                    .registerAdapter(new SlowFakeHarnessAdapter());
            coordinator.getHarnessRegistry().selectDefaultHarness("slow-fake");
            Path workspace = Files.createTempDirectory("dstudio-cross-ws");
            int port = coordinator.getServer().getBoundPort();
            HttpClient client = HttpClient.newHttpClient();

            // Web/TEIP client submits a coding task
            HttpResponse<String> created = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/teip/v1/executions"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"protocol": "TEIP", "version": "1.1",
                             "idempotencyKey": "cross-client-1",
                             "caller": {"system": "DSTUDIO_WEB"},
                             "capability": "CODE_IMPLEMENTATION", "mode": "DEFAULT",
                             "instruction": "Cross-client task",
                             "parameters": {"workingDirectory": "%s", "harness": "slow-fake"}}
                            """.formatted(workspace)))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(202, created.statusCode());
            String executionId = mapper.readTree(created.body()).get("executionId").asText();

            // RCP core sees the same execution (shared service, shared store)
            var task = coordinator.getExecutionService().getTask(executionId);
            assertTrue(task.isPresent(), "task must be visible to the RCP core");
            assertEquals("slow-fake", task.get().getHarnessId());

            // diagnostics surface reports it for any client
            HttpResponse<String> diagnostics = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/diagnostics"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(diagnostics.body().contains(executionId),
                    "diagnostics must expose the web-submitted task");
            assertTrue(diagnostics.body().contains("\"harnesses\""));

            // web client adds a runtime instance — the RCP registry sees it instantly
            HttpResponse<String> added = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port
                            + "/api/inference/engines"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"type\":\"OLLAMA\",\"id\":\"ollama-cross\","
                                    + "\"config\":{\"endpoint\":\"http://127.0.0.1:11434\"}}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(201, added.statusCode());
            assertTrue(coordinator.getInferenceEngineRegistry()
                    .findAdapter("ollama-cross").isPresent(),
                    "runtime added over HTTP must be present in the RCP registry");

            // and the shared event stream carried the task events for both clients
            var events = coordinator.getExecutionService().getEvents(executionId, 0, 50);
            assertFalse(events.isEmpty(), "execution events must be shared");
            assertTrue(events.stream().anyMatch(event ->
                    event.type().name().equals("EXECUTION_QUEUED")));
        } finally {
            coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
        }
    }

    /** Harness that stays active for a few seconds so cross-client "active" state is observable. */
    private static final class SlowFakeHarnessAdapter implements eu.kalafatic.dstudio.core.harness.HarnessAdapter {

        @Override
        public String getHarnessId() {
            return "slow-fake";
        }

        @Override
        public String getHarnessName() {
            return "Slow Fake";
        }

        @Override
        public String getHarnessType() {
            return "SLOW_FAKE";
        }

        @Override
        public java.util.List<String> getCapabilities() {
            return java.util.List.of("CODE_IMPLEMENTATION");
        }

        @Override
        public eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor
                getCapabilityDescriptor() {
            return new eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor(
                    "slow-fake", "Slow Fake", "SLOW_FAKE",
                    java.util.List.of("CODE_IMPLEMENTATION"), java.util.List.of("DEFAULT"),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of(),
                    java.util.List.of("OLLAMA"));
        }

        @Override
        public eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult testConnection() {
            return eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult.ok("up");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public eu.kalafatic.dstudio.core.harness.HarnessExecutionResult execute(
                eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest request,
                eu.kalafatic.dstudio.core.harness.HarnessExecutionSink sink) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return eu.kalafatic.dstudio.core.harness.HarnessExecutionResult
                    .ok("slow fake done", java.util.List.of());
        }
    }
}
