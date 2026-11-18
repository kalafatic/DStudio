package eu.kalafatic.dstudio.core.inference;

import java.time.Instant;
import java.util.UUID;

/** One message (user/system/assistant) of an {@link InferenceSession} conversation. */
public record InferenceSessionMessage(String id, String sessionId, String role,
        String content, Instant createdAt) {

    public static InferenceSessionMessage of(String sessionId, String role, String content) {
        return new InferenceSessionMessage(UUID.randomUUID().toString(), sessionId, role,
                content == null ? "" : content, Instant.now());
    }
}
