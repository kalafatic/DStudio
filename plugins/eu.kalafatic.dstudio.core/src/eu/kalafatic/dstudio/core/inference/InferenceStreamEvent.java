package eu.kalafatic.dstudio.core.inference;

/**
 * Live direct-inference event published on the event bus (and carried by the global
 * SSE stream). Kinds: {@code CREATED} (session created), {@code CHUNK} (text fragment),
 * {@code DONE} (completed with timing), {@code CANCELLED}, {@code ERROR}.
 */
public record InferenceStreamEvent(String sessionId, String engineId, String model,
        String kind, String text, long durationMs, String error) {

    public static InferenceStreamEvent created(InferenceSession session) {
        return new InferenceStreamEvent(session.id(), session.engineId(), session.model(),
                "CREATED", "", 0, "");
    }

    public static InferenceStreamEvent chunk(String sessionId, String engineId, String model,
            String text) {
        return new InferenceStreamEvent(sessionId, engineId, model, "CHUNK",
                text == null ? "" : text, 0, "");
    }

    public static InferenceStreamEvent done(String sessionId, String engineId, String model,
            long durationMs) {
        return new InferenceStreamEvent(sessionId, engineId, model, "DONE", "",
                durationMs, "");
    }

    public static InferenceStreamEvent cancelled(String sessionId, String engineId,
            String model) {
        return new InferenceStreamEvent(sessionId, engineId, model, "CANCELLED", "", 0, "");
    }

    public static InferenceStreamEvent error(String sessionId, String engineId, String model,
            String message) {
        return new InferenceStreamEvent(sessionId, engineId, model, "ERROR", "", 0,
                message == null ? "" : message);
    }
}
