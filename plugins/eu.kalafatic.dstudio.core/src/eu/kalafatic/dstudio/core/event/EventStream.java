package eu.kalafatic.dstudio.core.event;

import java.util.List;

/**
 * Global, backend-owned event stream shared by all clients (RCP, Web IDE, TEIP
 * consumers). Every event published on the {@link EventBus} receives a monotonically
 * increasing sequence number and is buffered for a bounded window so that clients can
 * reconnect and replay what they missed.
 *
 * <p>The stream is a convenience channel, not a durable record: clients must treat it
 * as lossy across restarts and always reconcile against the authoritative REST state
 * after reconnecting.</p>
 */
public interface EventStream {

    /** Records one event with the next sequence number. */
    void record(Object event);

    /** Sequence of the most recently recorded event (0 when nothing recorded yet). */
    long lastSequence();

    /**
     * Returns buffered entries with sequence &gt; {@code afterSequence} in ascending
     * order, capped at {@code maxEntries} (older entries are dropped first).
     */
    List<EventEntry> after(long afterSequence, int maxEntries);

    /**
     * Blocks until an event with sequence &gt; {@code afterSequence} is recorded or the
     * timeout elapses; returns the current {@link #lastSequence()} (which may equal
     * {@code afterSequence} on timeout).
     */
    long awaitNext(long afterSequence, long timeoutMillis);

    int getCapacity();
}
