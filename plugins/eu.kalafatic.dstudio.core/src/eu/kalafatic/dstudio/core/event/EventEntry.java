package eu.kalafatic.dstudio.core.event;

import java.time.Instant;

/**
 * One record of the global event stream. The payload is the original event object
 * published on the {@link EventBus} (e.g. {@code ExecutionEvent},
 * {@code InferenceStreamEvent}); it must be JSON-serializable for wire delivery.
 */
public record EventEntry(long sequence, Instant timestamp, String type, Object payload) {
}
