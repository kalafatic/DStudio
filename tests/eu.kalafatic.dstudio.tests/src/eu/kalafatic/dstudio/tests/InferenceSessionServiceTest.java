package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceService;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceException;
import eu.kalafatic.dstudio.core.inference.InferenceService;
import eu.kalafatic.dstudio.core.inference.InferenceSession;
import eu.kalafatic.dstudio.core.inference.InferenceSessionMessage;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceEngineStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceSessionStore;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7 (S4): direct-inference sessions must persist multi-turn history, pin their
 * engine/model, fail visibly (never fall back), and honour cooperative cancellation.
 */
class InferenceSessionServiceTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private DefaultInferenceEngineRegistry registry;
    private DefaultInferenceService service;
    private FakeInferenceEngineAdapter enr;

    @BeforeEach
    void setUp() {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("session-service.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        registry = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        enr = new FakeInferenceEngineAdapter("enr", "ENR");
        registry.registerEngine(enr);
        registry.registerEngine(new FakeInferenceEngineAdapter("ollama", "OLLAMA"));
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
    void createSessionPinsEngineAndModel() {
        InferenceSession session = service.createSession("enr", "demo:1", "be terse");
        assertEquals("enr", session.engineId());
        assertEquals("demo:1", session.model());
        assertEquals("be terse", session.systemPrompt());
        assertEquals(InferenceSession.Status.OPEN, session.status());
        assertEquals(1, service.listSessions().size());
        assertEquals(session.id(), service.getSession(session.id()).orElseThrow().id());
    }

    @Test
    void sendMessageBuildsFullHistory() {
        InferenceSession session = service.createSession("enr", "demo:1", "sys");
        InferenceService.SendResult first = service.sendMessage(session.id(), "hello");
        assertEquals("fake answer", first.content());
        assertEquals("stop", first.finishReason());
        InferenceService.SendResult second = service.sendMessage(session.id(), "again");
        assertEquals("fake answer", second.content());

        List<InferenceSessionMessage> messages = service.getMessages(session.id());
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(0).role());
        assertEquals("hello", messages.get(0).content());
        assertEquals("assistant", messages.get(1).role());
        assertEquals("user", messages.get(2).role());
        assertEquals("again", messages.get(2).content());
        assertEquals("assistant", messages.get(3).role());

        // the engine received system prompt + history (incl. the new user message)
        InferenceEngineAdapter.InferenceChatRequest request = enr.getLastRequest();
        assertEquals("demo:1", request.model());
        assertEquals(4, request.messages().size());
        assertEquals("sys", request.messages().get(0).content());
        assertEquals("system", request.messages().get(0).role());
        assertEquals("again", request.messages().get(3).content());
    }

    @Test
    void cancelDuringRunMarksSessionCancelled() throws Exception {
        SlowEngine slow = new SlowEngine();
        registry.registerEngine(slow);
        InferenceSession session = service.createSession("slow", "slow-model", null);
        AtomicBoolean started = new AtomicBoolean();
        Thread sender = new Thread(() -> {
            try {
                started.set(true);
                service.sendMessage(session.id(), "stream forever");
            } catch (Exception ignored) {
            }
        });
        sender.start();
        long deadline = System.currentTimeMillis() + 5000;
        while (!started.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        Thread.sleep(200);
        service.cancel(session.id());
        sender.join(5000);
        assertFalse(sender.isAlive(), "cancel must abort the running stream");
        assertEquals(InferenceSession.Status.CANCELLED,
                service.getSession(session.id()).orElseThrow().status());
    }

    @Test
    void cancelIdleSessionMarksCancelled() {
        InferenceSession session = service.createSession("enr", "demo:1", null);
        service.cancel(session.id());
        assertEquals(InferenceSession.Status.CANCELLED,
                service.getSession(session.id()).orElseThrow().status());
        // idempotent
        service.cancel(session.id());
        assertEquals(InferenceSession.Status.CANCELLED,
                service.getSession(session.id()).orElseThrow().status());
    }

    @Test
    void engineFailureMarksSessionFailedAndNeverFallsBack() {
        FakeInferenceEngineAdapter failing = new FakeInferenceEngineAdapter("failing", "FAIL");
        failing.setFail(true);
        registry.registerEngine(failing);
        InferenceSession session = service.createSession("failing", "m", null);
        InferenceException thrown = assertThrows(InferenceException.class,
                () -> service.sendMessage(session.id(), "hi"));
        assertEquals(InferenceException.Code.INFERENCE_FAILED, thrown.code());
        assertEquals(InferenceSession.Status.FAILED,
                service.getSession(session.id()).orElseThrow().status());
        assertTrue(service.getSession(session.id()).orElseThrow().statusMessage()
                .contains("fake engine failure"));
        // terminal session cannot accept new messages
        InferenceException terminal = assertThrows(InferenceException.class,
                () -> service.sendMessage(session.id(), "hi again"));
        assertEquals(InferenceException.Code.CONFLICT, terminal.code());
    }

    @Test
    void unknownEngineAndSessionAreNotFound() {
        InferenceException unknownEngine = assertThrows(InferenceException.class,
                () -> service.createSession("missing", "m", null));
        assertEquals(InferenceException.Code.NOT_FOUND, unknownEngine.code());
        InferenceException unknownSession = assertThrows(InferenceException.class,
                () -> service.sendMessage("inf-missing", "hi"));
        assertEquals(InferenceException.Code.NOT_FOUND, unknownSession.code());
    }

    @Test
    void disabledEngineIsRejected() {
        registry.setEngineEnabled("ollama", false);
        InferenceException disabled = assertThrows(InferenceException.class,
                () -> service.createSession("ollama", "m", null));
        assertEquals(InferenceException.Code.CONFLICT, disabled.code());
    }

    /** Engine that streams text chunks until cancelled (never completes on its own). */
    private static final class SlowEngine implements InferenceEngineAdapter {

        @Override
        public String getEngineId() {
            return "slow";
        }

        @Override
        public String getEngineName() {
            return "Slow Engine";
        }

        @Override
        public String getEngineType() {
            return "SLOW";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public InferenceConnectionTestResult testConnection() {
            return InferenceConnectionTestResult.ok("slow available");
        }

        @Override
        public List<InferenceModel> listModels() {
            return List.of(new InferenceModel("slow-model", "slow-model", "slow", "slow",
                    1, true));
        }

        @Override
        public InferenceRuntimeStatus runtimeStatus() {
            return new InferenceRuntimeStatus(true, 0, 0, 0, 0, false, "", 0, 0, 0,
                    List.of());
        }

        @Override
        public boolean loadModel(String modelId) {
            return true;
        }

        @Override
        public boolean unloadModel(String modelId) {
            return true;
        }

        @Override
        public InferenceChatResponse chat(InferenceChatRequest request) {
            throw new UnsupportedOperationException("slow engine streams only");
        }

        @Override
        public void streamChat(InferenceChatRequest request,
                java.util.function.Consumer<InferenceChatChunk> chunkConsumer,
                java.util.function.BooleanSupplier cancellationSupplier) {
            while (!cancellationSupplier.getAsBoolean()) {
                chunkConsumer.accept(new InferenceChatChunk("x", false, "slow"));
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            throw new IllegalStateException("stream aborted by cancellation");
        }
    }
}
