package eu.kalafatic.dstudio.core.inference;

import java.time.Instant;

/**
 * A persisted direct-inference conversation with a selected engine/model. Direct
 * inference sessions are distinct from coding tasks and harness sessions: they carry
 * multi-turn history, a system prompt and lifecycle state, and are owned by the
 * backend so every client sees the same conversation.
 */
public record InferenceSession(String id, String engineId, String model, String systemPrompt,
        Status status, String statusMessage, Instant createdAt, Instant updatedAt,
        String title) {

    public enum Status {
        OPEN, CANCELLED, FAILED
    }

    public static InferenceSession of(String id, String engineId, String model,
            String systemPrompt, String title) {
        Instant now = Instant.now();
        return new InferenceSession(id, engineId, model,
                systemPrompt == null ? "" : systemPrompt, Status.OPEN, "", now, now,
                title == null || title.isBlank() ? "Untitled" : title);
    }
}
