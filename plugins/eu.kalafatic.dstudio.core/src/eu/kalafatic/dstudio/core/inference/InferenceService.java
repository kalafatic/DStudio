package eu.kalafatic.dstudio.core.inference;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Backend-owned direct-inference orchestration: persisted multi-turn sessions over any
 * registered inference engine, with streaming, cancellation and live events. Direct
 * inference never touches coding harnesses and never silently switches engines or
 * models — the configured selection either works or fails visibly.
 */
public interface InferenceService {

    /** Creates a persisted session for the given engine/model; publishes CREATED. */
    InferenceSession createSession(String engineId, String model, String systemPrompt);

    /** Creates a persisted session with a user-visible title. */
    default InferenceSession createSession(String engineId, String model,
            String systemPrompt, String title) {
        return createSession(engineId, model, systemPrompt);
    }

    /** Renames a session; the title must not be blank. */
    InferenceSession renameSession(String sessionId, String title);

    /** Deletes a session and its message history. Idempotent for unknown ids. */
    void deleteSession(String sessionId);

    List<InferenceSession> listSessions();

    Optional<InferenceSession> getSession(String sessionId);

    List<InferenceSessionMessage> getMessages(String sessionId);

    /** Sends a user message and blocks until the assistant answer is complete. */
    SendResult sendMessage(String sessionId, String content);

    /**
     * Sends a user message and streams chunks to {@code chunkConsumer}; throws when the
     * session is missing/terminal or the engine fails.
     */
    void streamMessage(String sessionId, String content,
            Consumer<InferenceEngineAdapter.InferenceChatChunk> chunkConsumer);

    /** Cooperative cancel of the running send; idempotent. */
    void cancel(String sessionId);

    record SendResult(String content, String finishReason, String model, String engine,
            long durationMs) {
    }
}
