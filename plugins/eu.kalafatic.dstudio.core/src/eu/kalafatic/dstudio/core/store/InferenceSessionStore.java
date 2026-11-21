package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.inference.InferenceSession;
import eu.kalafatic.dstudio.core.inference.InferenceSessionMessage;

import java.util.List;
import java.util.Optional;

/** Persistence contract for direct-inference sessions and their messages. */
public interface InferenceSessionStore {

    void saveSession(InferenceSession session);

    Optional<InferenceSession> findSession(String sessionId);

    List<InferenceSession> listSessions();

    void addMessage(InferenceSessionMessage message);

    List<InferenceSessionMessage> listMessages(String sessionId);

    /** Deletes a session and its messages. Idempotent for unknown ids. */
    void deleteSession(String sessionId);
}
