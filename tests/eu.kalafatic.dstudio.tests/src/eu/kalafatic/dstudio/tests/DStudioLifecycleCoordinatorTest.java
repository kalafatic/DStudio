package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class DStudioLifecycleCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void headlessLifecycleStartsServerAndStopsCleanly() throws Exception {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("dstudio-home"));
        Files.createDirectories(paths.getConfigurationDirectory());
        Files.writeString(paths.getPropertiesFile(), "teip.devMode=true\nteip.port=0\n");
        DStudioLifecycleCoordinator coordinator = new DStudioLifecycleCoordinator(paths);
        coordinator.advance(DStudioLifecycleStage.APPLICATION_READY);
        assertEquals(DStudioLifecycleStage.APPLICATION_READY, coordinator.getStage());
        assertNotNull(coordinator.getConfigurationService());
        assertNotNull(coordinator.getHarnessRegistry());
        assertNotNull(coordinator.getExecutionService());
        assertNotNull(coordinator.getProjectService());
        assertNotNull(coordinator.getReviewService());
        assertNotNull(coordinator.getEventBus());
        assertNotNull(coordinator.getServer());
        assertTrue(coordinator.getServer().isRunning());
        assertTrue(coordinator.getServer().getBoundPort() > 0);
        assertTrue(Files.isRegularFile(paths.getDatabaseFile()));
        int port = coordinator.getServer().getBoundPort();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> status = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/status"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"status\":\"UP\""));
        assertTrue(status.body().contains("DSTUDIO"));
        HttpResponse<String> diagnostics = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/diagnostics"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, diagnostics.statusCode());
        assertTrue(diagnostics.body().contains("harnesses"));
        coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
        assertEquals(DStudioLifecycleStage.APPLICATION_TERMINATED, coordinator.getStage());
        assertFalse(coordinator.getServer().isRunning());
    }

    @Test
    void stageAdvanceIsMonotonic() throws Exception {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("dstudio-home2"));
        Files.createDirectories(paths.getConfigurationDirectory());
        Files.writeString(paths.getPropertiesFile(), "teip.port=0\n");
        DStudioLifecycleCoordinator coordinator = new DStudioLifecycleCoordinator(paths);
        coordinator.advance(DStudioLifecycleStage.APPLICATION_READY);
        coordinator.advance(DStudioLifecycleStage.CORE_SERVICE_INITIALIZATION);
        assertEquals(DStudioLifecycleStage.APPLICATION_READY, coordinator.getStage());
        coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
    }

    @Test
    void desktopSubmittedTaskVisibleThroughTeipSurface() throws Exception {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("dstudio-home3"));
        Files.createDirectories(paths.getConfigurationDirectory());
        Files.writeString(paths.getPropertiesFile(), "teip.devMode=true\nteip.port=0\n");
        DStudioLifecycleCoordinator coordinator = new DStudioLifecycleCoordinator(paths);
        coordinator.advance(DStudioLifecycleStage.APPLICATION_READY);
        coordinator.getHarnessRegistry()
                .registerAdapter(new FakeHarnessAdapter("fake", "FAKE"));
        coordinator.getHarnessRegistry().selectDefaultHarness("fake");
        Path workspace = tempDir.resolve("cross-mode-ws");
        Files.createDirectories(workspace);
        eu.kalafatic.dstudio.core.model.ExecutionTask task =
                coordinator.getExecutionService().submit(
                        new eu.kalafatic.dstudio.core.service.ExecutionService.ExecutionRequest(
                                "fake", null, null, "Cross-mode task", null, null, null,
                                false, workspace.toString(),
                                java.time.Duration.ofMinutes(1)));
        int port = coordinator.getServer().getBoundPort();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> status = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port
                        + "/teip/v1/executions/" + task.getId()))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains(task.getId()));
        HttpResponse<String> events = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port
                        + "/teip/v1/executions/" + task.getId()
                        + "/events?afterSequence=0&limit=50"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, events.statusCode());
        assertTrue(events.body().contains("EXECUTION_QUEUED"));
        coordinator.advance(DStudioLifecycleStage.APPLICATION_TERMINATED);
    }
}
