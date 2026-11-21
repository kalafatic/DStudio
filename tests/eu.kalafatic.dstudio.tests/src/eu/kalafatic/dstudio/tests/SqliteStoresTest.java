package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.model.ChangeReview;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.core.model.ExecutionStatus;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.core.model.ReviewDecision;
import eu.kalafatic.dstudio.core.store.ExecutionStore;
import eu.kalafatic.dstudio.core.store.ReviewStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteExecutionStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteReviewStore;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStoresTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private ExecutionStore executionStore;
    private ReviewStore reviewStore;

    @BeforeEach
    void setUp() {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("store-test.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        executionStore = new SqliteExecutionStore(databaseManager.connection());
        reviewStore = new SqliteReviewStore(databaseManager.connection());
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void harnessDescriptorRoundTrip() {
        SqliteHarnessStore store = new SqliteHarnessStore(databaseManager.connection(),
                new SecretCodec(tempDir));
        HarnessDescriptor descriptor = new HarnessDescriptor("opencode", "OpenCode CLI",
                "OPENCODE", List.of("CODE_IMPLEMENTATION"), true,
                HarnessDescriptor.HarnessStatus.CONNECTED, "ready",
                Instant.now(), Instant.now());
        store.saveDescriptor(descriptor);
        HarnessDescriptor loaded = store.findDescriptor("opencode").orElseThrow();
        assertEquals("opencode", loaded.getId());
        assertEquals("OPENCODE", loaded.getType());
        assertTrue(loaded.isEnabled());
        assertEquals(HarnessDescriptor.HarnessStatus.CONNECTED, loaded.getStatus());
        store.saveConfiguration("opencode",
                Map.of("model", "ollama/qwen3-coder-30b-32k:latest", "agent", "build"));
        Map<String, String> config = store.findConfiguration("opencode");
        assertEquals("build", config.get("agent"));
    }

    @Test
    void executionRoundTripPreservesStatus() {
        ExecutionTask task = new ExecutionTask("task-1", "opencode", "proj-1",
                "idem-1", "instruction", "prompt", "ollama/test:latest", "build", true,
                "/tmp/ws", Instant.now());
        executionStore.saveExecution(task);
        task.markRunning();
        executionStore.updateExecution(task);
        ExecutionTask loaded = executionStore.findExecution("task-1").orElseThrow();
        assertEquals(ExecutionStatus.RUNNING, loaded.getStatus());
        assertEquals("ollama/test:latest", loaded.getModel());
        assertEquals("build", loaded.getAgent());
        task.markCompleted("done", 0);
        executionStore.updateExecution(task);
        loaded = executionStore.findExecution("task-1").orElseThrow();
        assertEquals(ExecutionStatus.COMPLETED, loaded.getStatus());
        assertEquals("done", loaded.getSummary());
    }

    @Test
    void eventsSequenceAndCursor() {
        executionStore.appendEvent(ExecutionEvent.of(1, "task-1",
                ExecutionEventType.EXECUTION_QUEUED, "queued"));
        executionStore.appendEvent(ExecutionEvent.of(2, "task-1",
                ExecutionEventType.EXECUTION_STARTED, "started"));
        executionStore.appendEvent(ExecutionEvent.of(1, "task-2",
                ExecutionEventType.EXECUTION_QUEUED, "queued"));
        assertEquals(2, executionStore.nextSequence("task-1"));
        assertEquals(1, executionStore.nextSequence("task-2"));
        List<ExecutionEvent> events = executionStore.loadEvents("task-1", 0, 50);
        assertEquals(2, events.size());
        assertEquals(1, events.get(0).sequence());
        List<ExecutionEvent> tail = executionStore.loadEvents("task-1", 1, 50);
        assertEquals(1, tail.size());
        assertEquals(2, tail.get(0).sequence());
        assertEquals("task-1", tail.get(0).executionId());
        List<ExecutionEvent> limited = executionStore.loadEvents("task-1", 0, 1);
        assertEquals(1, limited.size());
    }

    @Test
    void idempotencyPersistsAcrossStoreInstances() {
        ExecutionTask task = new ExecutionTask("task-42", "opencode", null,
                "idem-42", "instruction", null, null, null, false, "/tmp/ws",
                Instant.now());
        executionStore.saveExecution(task);
        executionStore.saveIdempotency("idem-42", "task-42");
        ExecutionStore second = new SqliteExecutionStore(databaseManager.connection());
        assertTrue(second.findExecutionByIdempotencyKey("idem-42").isPresent());
        assertEquals("task-42",
                second.findExecutionByIdempotencyKey("idem-42").orElseThrow().getId());
        assertTrue(second.findExecutionByIdempotencyKey("missing").isEmpty());
    }

    @Test
    void openExecutionsListing() {
        ExecutionTask running = new ExecutionTask("task-run", "opencode", null, null,
                "i", null, null, null, false, "/tmp/ws", Instant.now());
        running.markRunning();
        executionStore.saveExecution(running);
        ExecutionTask done = new ExecutionTask("task-done", "opencode", null, null,
                "i", null, null, null, false, "/tmp/ws", Instant.now());
        done.markCompleted("ok", 0);
        executionStore.saveExecution(done);
        List<ExecutionTask> open = executionStore.listOpenExecutions();
        assertEquals(1, open.size());
        assertEquals("task-run", open.get(0).getId());
        assertEquals(2, executionStore.listExecutions().size());
    }

    @Test
    void reviewRoundTripWithDecisions() {
        ChangeReview review = new ChangeReview("review-1", "task-1", List.of(
                new ChangeReview.ReviewFile("src/A.java", "diff-a", ReviewDecision.PENDING),
                new ChangeReview.ReviewFile("src/B.java", "diff-b", ReviewDecision.PENDING)));
        reviewStore.saveReview(review);
        ChangeReview loaded = reviewStore.findReview("review-1").orElseThrow();
        assertEquals(2, loaded.getFiles().size());
        assertEquals(ChangeReview.ReviewState.OPEN, loaded.getState());
        loaded.setDecision("src/A.java", ReviewDecision.ACCEPTED);
        loaded.setDecision("src/B.java", ReviewDecision.REJECTED);
        loaded.updateState();
        reviewStore.saveReview(loaded);
        ChangeReview reloaded = reviewStore.findReviewByExecution("task-1").orElseThrow();
        assertEquals(ChangeReview.ReviewState.DECIDED, reloaded.getState());
        assertEquals(ReviewDecision.REJECTED, reloaded.getFiles().stream()
                .filter(f -> f.path().equals("src/B.java")).findFirst()
                .orElseThrow().decision());
    }

    @Test
    void migrationsAreIdempotent() {
        new SchemaMigrationService().migrate(databaseManager.connection());
        executionStore.appendEvent(ExecutionEvent.of(1, "task-9",
                ExecutionEventType.EXECUTION_QUEUED, "after re-migrate"));
        assertFalse(executionStore.loadEvents("task-9", 0, 10).isEmpty());
    }

    @Test
    void migrationV4RecoversFromCrashBetweenDdlAndVersionInsert() throws Exception {
        Path db = tempDir.resolve("v4-crash.db");
        try (SqliteDatabaseManager manager = new SqliteDatabaseManager(db)) {
            Connection connection = manager.connection();
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, "
                        + "applied_at TEXT NOT NULL)");
                statement.execute("INSERT INTO schema_version (version, applied_at) "
                        + "VALUES (1, 'now')");
                statement.execute("INSERT INTO schema_version (version, applied_at) "
                        + "VALUES (2, 'now')");
                statement.execute("INSERT INTO schema_version (version, applied_at) "
                        + "VALUES (3, 'now')");
                statement.execute("CREATE TABLE executions (id TEXT PRIMARY KEY)");
                // Simulated crash: two of the three V4 ALTER statements were applied
                // before the crash; the schema_version insert never happened.
                statement.execute("ALTER TABLE executions ADD COLUMN provider_id "
                        + "TEXT NOT NULL DEFAULT ''");
                statement.execute("ALTER TABLE executions ADD COLUMN agent_profile_id "
                        + "TEXT NOT NULL DEFAULT ''");
            }
            new SchemaMigrationService().migrate(connection);
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery(
                            "PRAGMA table_info(executions)")) {
                java.util.Set<String> columns = new java.util.HashSet<>();
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
                assertTrue(columns.contains("provider_id"));
                assertTrue(columns.contains("agent_profile_id"));
                assertTrue(columns.contains("exec_params_json"));
            }
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery(
                            "SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                assertTrue(rs.next());
                assertEquals(SchemaMigrationService.LATEST_VERSION, rs.getInt(1));
            }
        }
    }
}
