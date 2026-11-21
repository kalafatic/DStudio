package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceService;
import eu.kalafatic.dstudio.core.inference.InferenceException;
import eu.kalafatic.dstudio.core.inference.InferenceSession;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceEngineStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceSessionStore;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Inference-session rename/delete contract (inference workspace session navigation).
 */
class InferenceSessionManagementTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private DefaultInferenceService service;

    @BeforeEach
    void setUp() {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("manage.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        DefaultInferenceEngineRegistry registry = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        registry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        registry.selectDefaultEngine("enr");
        service = new DefaultInferenceService(registry,
                new SqliteInferenceSessionStore(databaseManager.connection()),
                new DefaultEventBus());
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    @Test
    void createWithTitleAndRename() {
        InferenceSession session = service.createSession("enr", "demo:1", "sys",
                "My chat");
        assertEquals("My chat", session.title());
        InferenceSession renamed = service.renameSession(session.id(), "Renamed");
        assertEquals("Renamed", renamed.title());
        assertEquals("Renamed", service.getSession(session.id()).orElseThrow().title());
        // reloaded over the same database sees the title
        InferenceSession reloaded = new DefaultInferenceService(
                new DefaultInferenceEngineRegistry(new SqliteInferenceEngineStore(
                        databaseManager.connection(), new SecretCodec(tempDir))),
                new SqliteInferenceSessionStore(databaseManager.connection()),
                new DefaultEventBus()).getSession(session.id()).orElseThrow();
        assertEquals("Renamed", reloaded.title());
    }

    @Test
    void renameRejectsBlankTitle() {
        InferenceSession session = service.createSession("enr", "demo:1", "sys", "t");
        assertThrows(InferenceException.class,
                () -> service.renameSession(session.id(), "  "));
    }

    @Test
    void deleteRemovesSessionAndMessages() {
        InferenceSession session = service.createSession("enr", "demo:1", "sys", "t");
        service.sendMessage(session.id(), "hello");
        assertTrue(!service.getMessages(session.id()).isEmpty());
        service.deleteSession(session.id());
        assertTrue(service.getSession(session.id()).isEmpty());
        assertTrue(service.getMessages(session.id()).isEmpty());
        // idempotent
        service.deleteSession(session.id());
    }
}
