package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.event.EventBus;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionStatus;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.service.DefaultExecutionService;
import eu.kalafatic.dstudio.core.service.DefaultReviewService;
import eu.kalafatic.dstudio.core.service.ExecutionService;
import eu.kalafatic.dstudio.core.service.ReviewService;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteExecutionStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteReviewStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultExecutionServiceTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private HarnessRegistry registry;
    private EventBus eventBus;
    private ExecutionService executionService;
    private ReviewService reviewService;
    private FakeHarnessAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("exec-test.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                new SecretCodec(tempDir)));
        adapter = new FakeHarnessAdapter("fake", "FAKE");
        registry.registerAdapter(adapter);
        eventBus = new DefaultEventBus();
        reviewService = new DefaultReviewService(
                new SqliteReviewStore(databaseManager.connection()));
        executionService = new DefaultExecutionService(registry,
                new SqliteExecutionStore(databaseManager.connection()),
                new SqliteReviewStore(databaseManager.connection()), null, eventBus);
    }

    @AfterEach
    void tearDown() {
        executionService.shutdown();
        databaseManager.close();
    }

    private Path workspace() throws Exception {
        Path dir = tempDir.resolve("workspace-" + System.nanoTime());
        Files.createDirectories(dir);
        return dir;
    }

    private ExecutionService.ExecutionRequest request(Path workspace) {
        return request(workspace, null);
    }

    private ExecutionService.ExecutionRequest request(Path workspace, String idempotencyKey) {
        return new ExecutionService.ExecutionRequest("fake", null, idempotencyKey,
                "Test instruction", null, null, null, false, workspace.toString(),
                Duration.ofMinutes(2));
    }

    private ExecutionTask awaitTerminal(String executionId) throws Exception {
        for (int i = 0; i < 100; i++) {
            ExecutionTask task = executionService.getTask(executionId).orElseThrow();
            if (task.isTerminal()) {
                return task;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Task did not reach terminal state: " + executionId);
    }

    @Test
    void successfulExecutionCompletesWithReviewAndEvents() throws Exception {
        Path workspace = workspace();
        ExecutionTask submitted = executionService.submit(request(workspace));
        ExecutionTask task = awaitTerminal(submitted.getId());
        assertEquals(ExecutionStatus.COMPLETED, task.getStatus());
        assertTrue(task.getSummary().contains("fake summary"));
        assertTrue(reviewService.getReviewForExecution(submitted.getId()).isPresent());
        List<ExecutionEvent> events = executionService.getEvents(submitted.getId(), 0, 100);
        assertTrue(events.stream().anyMatch(e ->
                e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_QUEUED));
        assertTrue(events.stream().anyMatch(e ->
                e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_STARTED));
        assertTrue(events.stream().anyMatch(e ->
                e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_LOG));
        assertTrue(events.stream().anyMatch(e ->
                e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_COMPLETED));
        long maxSequence = events.stream().mapToLong(ExecutionEvent::sequence).max().orElse(0);
        assertTrue(maxSequence >= 3);
        List<ExecutionEvent> tail = executionService.getEvents(submitted.getId(),
                maxSequence, 100);
        assertTrue(tail.isEmpty());
    }

    @Test
    void eventBusReceivesPublishedEvents() throws Exception {
        Path workspace = workspace();
        java.util.concurrent.atomic.AtomicInteger counter =
                new java.util.concurrent.atomic.AtomicInteger();
        AutoCloseable subscription = eventBus.subscribe(ExecutionEvent.class,
                e -> counter.incrementAndGet());
        try {
            ExecutionTask submitted = executionService.submit(request(workspace));
            awaitTerminal(submitted.getId());
        } finally {
            subscription.close();
        }
        assertTrue(counter.get() >= 3);
    }

    @Test
    void idempotencyKeyReturnsSameTask() throws Exception {
        Path workspace = workspace();
        ExecutionTask first = executionService.submit(request(workspace, "idem-1"));
        ExecutionTask second = executionService.submit(request(workspace, "idem-1"));
        assertEquals(first.getId(), second.getId());
        awaitTerminal(first.getId());
        ExecutionTask third = executionService.submit(request(workspace, "idem-1"));
        assertEquals(first.getId(), third.getId());
        assertEquals(1, executionService.listTasks().size());
    }

    @Test
    void invalidHarnessRejected() throws Exception {
        Path workspace = workspace();
        ExecutionService.ExecutionRequest bad = new ExecutionService.ExecutionRequest(
                "missing", null, null, "instruction", null, null, null, false,
                workspace.toString(), Duration.ofMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> executionService.submit(bad));
    }

    @Test
    void disabledHarnessRejected() throws Exception {
        registry.setHarnessEnabled("fake", false);
        Path workspace = workspace();
        assertThrows(IllegalArgumentException.class,
                () -> executionService.submit(request(workspace)));
    }

    @Test
    void missingInstructionRejected() throws Exception {
        Path workspace = workspace();
        ExecutionService.ExecutionRequest bad = new ExecutionService.ExecutionRequest(
                "fake", null, null, null, null, null, null, false, workspace.toString(),
                Duration.ofMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> executionService.submit(bad));
    }

    @Test
    void cancelWhileRunningCancelsTask() throws Exception {
        adapter.setMode(FakeHarnessAdapter.Mode.HANG);
        Path workspace = workspace();
        ExecutionTask submitted = executionService.submit(request(workspace));
        assertTrue(adapter.awaitStart(5000));
        executionService.cancel(submitted.getId());
        ExecutionTask task = awaitTerminal(submitted.getId());
        assertEquals(ExecutionStatus.CANCELLED, task.getStatus());
        List<ExecutionEvent> events = executionService.getEvents(submitted.getId(), 0, 100);
        assertTrue(events.stream().anyMatch(e ->
                e.type() == eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_CANCELLED));
        executionService.cancel(submitted.getId());
    }

    @Test
    void timeoutProducesTimedOutStatus() throws Exception {
        adapter.setMode(FakeHarnessAdapter.Mode.SLOW);
        Path workspace = workspace();
        ExecutionService.ExecutionRequest shortTimeout = new ExecutionService.ExecutionRequest(
                "fake", null, null, "instruction", null, null, null, false,
                workspace.toString(), Duration.ofSeconds(1));
        ExecutionTask submitted = executionService.submit(shortTimeout);
        ExecutionTask task = awaitTerminal(submitted.getId());
        assertEquals(ExecutionStatus.TIMED_OUT, task.getStatus());
    }

    @Test
    void harnessFailureProducesFailedStatus() throws Exception {
        adapter.setMode(FakeHarnessAdapter.Mode.FAIL);
        Path workspace = workspace();
        ExecutionTask submitted = executionService.submit(request(workspace));
        ExecutionTask task = awaitTerminal(submitted.getId());
        assertEquals(ExecutionStatus.FAILED, task.getStatus());
        assertTrue(task.getErrorMessage().contains("Fake harness failed"));
    }

    @Test
    void reconcileAfterRestartFailsOpenExecutions() throws Exception {
        Path workspace = workspace();
        eu.kalafatic.dstudio.core.model.ExecutionTask orphan =
                new eu.kalafatic.dstudio.core.model.ExecutionTask("task-orphan01",
                        "fake", null, null, "instruction", null, null, null, false,
                        workspace.toString(), java.time.Instant.now());
        orphan.markRunning();
        new SqliteExecutionStore(databaseManager.connection()).saveExecution(orphan);
        ExecutionService second = new DefaultExecutionService(registry,
                new SqliteExecutionStore(databaseManager.connection()),
                new SqliteReviewStore(databaseManager.connection()), null, eventBus);
        try {
            second.reconcileAfterRestart();
            ExecutionTask restored = second.getTask("task-orphan01").orElseThrow();
            assertEquals(ExecutionStatus.FAILED, restored.getStatus());
            assertTrue(restored.getErrorMessage().contains("cannot be resumed"));
        } finally {
            second.shutdown();
        }
    }

    @Test
    void concurrentSubmissionsAreIsolated() throws Exception {
        Path workspace1 = workspace();
        Path workspace2 = workspace();
        ExecutionTask t1 = executionService.submit(request(workspace1));
        ExecutionTask t2 = executionService.submit(request(workspace2));
        assertEquals(ExecutionStatus.COMPLETED, awaitTerminal(t1.getId()).getStatus());
        assertEquals(ExecutionStatus.COMPLETED, awaitTerminal(t2.getId()).getStatus());
        assertFalse(t1.getId().equals(t2.getId()));
    }
}
