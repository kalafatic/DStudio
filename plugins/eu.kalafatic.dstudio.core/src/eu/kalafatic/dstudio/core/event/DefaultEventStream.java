package eu.kalafatic.dstudio.core.event;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded in-memory event stream fed by an {@link EventBus} subscription. Recording and
 * replay share one lock so that {@link #after(long, int)} + {@link #awaitNext(long, long)}
 * can be combined without gaps or duplicates.
 */
public class DefaultEventStream implements EventStream {

    public static final int DEFAULT_CAPACITY = 10_000;

    private final int capacity;
    private final Deque<EventEntry> buffer = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private long sequence;

    public DefaultEventStream(EventBus eventBus) {
        this(eventBus, DEFAULT_CAPACITY);
    }

    public DefaultEventStream(EventBus eventBus, int capacity) {
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus is required");
        }
        this.capacity = Math.max(1, capacity);
        eventBus.subscribe(Object.class, this::record);
    }

    @Override
    public void record(Object event) {
        if (event == null) {
            return;
        }
        lock.lock();
        try {
            sequence++;
            buffer.addLast(new EventEntry(sequence, Instant.now(),
                    event.getClass().getSimpleName(), event));
            while (buffer.size() > capacity) {
                buffer.removeFirst();
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long lastSequence() {
        lock.lock();
        try {
            return sequence;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<EventEntry> after(long afterSequence, int maxEntries) {
        int limit = Math.max(1, Math.min(maxEntries, capacity));
        lock.lock();
        try {
            List<EventEntry> result = new ArrayList<>();
            for (EventEntry entry : buffer) {
                if (entry.sequence() > afterSequence) {
                    result.add(entry);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long awaitNext(long afterSequence, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMillis);
        lock.lock();
        try {
            while (sequence <= afterSequence) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return sequence;
                }
                changed.awaitNanos(java.util.concurrent.TimeUnit.MILLISECONDS
                        .toNanos(remaining));
            }
            return sequence;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return sequence;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getCapacity() {
        return capacity;
    }
}
