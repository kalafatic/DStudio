package eu.kalafatic.dstudio.core.model;

import java.time.Instant;
import java.util.Map;

public record ExecutionEvent(long sequence, String executionId, ExecutionEventType type,
        Instant timestamp, String message, Map<String, Object> data) {

    public static ExecutionEvent of(long sequence, String executionId, ExecutionEventType type,
            String message) {
        return new ExecutionEvent(sequence, executionId, type, Instant.now(), message, Map.of());
    }

    public static ExecutionEvent of(long sequence, String executionId, ExecutionEventType type,
            String message, Map<String, Object> data) {
        return new ExecutionEvent(sequence, executionId, type, Instant.now(), message,
                data == null ? Map.of() : data);
    }
}
