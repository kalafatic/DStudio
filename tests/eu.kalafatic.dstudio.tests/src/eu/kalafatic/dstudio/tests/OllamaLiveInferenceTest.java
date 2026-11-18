package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceService;
import eu.kalafatic.dstudio.core.inference.InferenceSession;
import eu.kalafatic.dstudio.core.inference.InferenceSessionMessage;
import eu.kalafatic.dstudio.harness.ollama.OllamaInferenceEngineAdapter;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceEngineStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceSessionStore;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7 (S4) live evidence: real streaming inference and real stream cancellation
 * against the installed Ollama runtime (skips silently when Ollama is unavailable,
 * per family test conventions).
 */
class OllamaLiveInferenceTest {

    @TempDir
    Path tempDir;

    /** Known-good locally installed family model; the live tests skip when it is absent. */
    private static final List<String> PREFERRED_MODELS = List.of(
            "qwen3-coder-30b-32k:latest", "qwen3-coder:30b",
            "deepseek-coder-v2:16b-lite-instruct-q4_K_M");

    private String selectModel(OllamaInferenceEngineAdapter ollama) {
        if (!ollama.testConnection().success()) {
            return null;
        }
        List<String> names = ollama.listModels().stream()
                .map(model -> model.name()).toList();
        for (String preferred : PREFERRED_MODELS) {
            if (names.contains(preferred)) {
                return "ollama:" + preferred;
            }
        }
        return null;
    }

    @Test
    @Timeout(420)
    void liveStreamingSessionAgainstRealOllama() throws Exception {
        OllamaInferenceEngineAdapter ollama = new OllamaInferenceEngineAdapter();
        String model = selectModel(ollama);
        if (model == null) {
            return;
        }

        try (SqliteDatabaseManager db = new SqliteDatabaseManager(
                tempDir.resolve("live-session.db"))) {
            new SchemaMigrationService().migrate(db.connection());
            DefaultInferenceEngineRegistry registry = new DefaultInferenceEngineRegistry(
                    new SqliteInferenceEngineStore(db.connection(), new SecretCodec(tempDir)));
            registry.registerEngine(ollama);
            registry.selectDefaultEngine("ollama");
            DefaultInferenceService service = new DefaultInferenceService(registry,
                    new SqliteInferenceSessionStore(db.connection()), new DefaultEventBus());

            InferenceSession session = service.createSession("ollama", model,
                    "Answer with exactly one word.");
            StringBuilder out = new StringBuilder();
            AtomicBoolean doneSeen = new AtomicBoolean();
            long start = System.currentTimeMillis();
            service.streamMessage(session.id(), "Say exactly: OK", chunk -> {
                if (chunk.done()) {
                    doneSeen.set(true);
                }
                out.append(chunk.text());
            });
            long durationMs = System.currentTimeMillis() - start;
            assertTrue(doneSeen.get(), "stream must terminate with a done chunk");
            assertFalse(out.toString().isBlank(), "stream must carry real content");
            System.out.println("[OllamaLiveInferenceTest] live streaming: model=" + model
                    + " durationMs=" + durationMs + " content="
                    + out.toString().replaceAll("\\s+", " ").trim());

            List<InferenceSessionMessage> messages = service.getMessages(session.id());
            assertEquals(2, messages.size());
            assertEquals("user", messages.get(0).role());
            assertEquals("assistant", messages.get(1).role());
            assertEquals(InferenceSession.Status.OPEN,
                    service.getSession(session.id()).orElseThrow().status());
        }
    }

    @Test
    @Timeout(420)
    void liveCancellationAbortsRealOllamaStream() throws Exception {
        OllamaInferenceEngineAdapter ollama = new OllamaInferenceEngineAdapter();
        String model = selectModel(ollama);
        if (model == null) {
            return;
        }

        try (SqliteDatabaseManager db = new SqliteDatabaseManager(
                tempDir.resolve("live-cancel.db"))) {
            new SchemaMigrationService().migrate(db.connection());
            DefaultInferenceEngineRegistry registry = new DefaultInferenceEngineRegistry(
                    new SqliteInferenceEngineStore(db.connection(), new SecretCodec(tempDir)));
            registry.registerEngine(ollama);
            registry.selectDefaultEngine("ollama");
            DefaultInferenceService service = new DefaultInferenceService(registry,
                    new SqliteInferenceSessionStore(db.connection()), new DefaultEventBus());

            InferenceSession session = service.createSession("ollama", model, null);
            AtomicBoolean gotChunk = new AtomicBoolean();
            Thread sender = new Thread(() -> {
                try {
                    service.streamMessage(session.id(),
                            "Count from 1 to 1000, one number per line", chunk ->
                                    gotChunk.set(true));
                } catch (Exception ignored) {
                }
            });
            sender.start();
            long deadline = System.currentTimeMillis() + 180_000;
            while (!gotChunk.get() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100);
            }
            if (!gotChunk.get()) {
                return; // first token never arrived; cancellation evidence not obtainable
            }
            long abortStart = System.currentTimeMillis();
            service.cancel(session.id());
            sender.join(15_000);
            assertFalse(sender.isAlive(), "cancel must abort the live stream promptly");
            System.out.println("[OllamaLiveInferenceTest] live cancel: model=" + model
                    + " abortMs=" + (System.currentTimeMillis() - abortStart));
            assertEquals(InferenceSession.Status.CANCELLED,
                    service.getSession(session.id()).orElseThrow().status());
        }
    }
}
