package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.event.DefaultEventStream;
import eu.kalafatic.dstudio.core.event.EventEntry;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Phase 7 (S4): the global event stream must deliver ordered sequences, replay missed
 * windows and evict old entries beyond capacity.
 */
class EventStreamTest {

    @Test
    void recordsInOrderWithReplay() {
        DefaultEventBus bus = new DefaultEventBus();
        DefaultEventStream stream = new DefaultEventStream(bus, 100);
        assertEquals(0, stream.lastSequence());
        bus.publish("first");
        bus.publish("second");
        assertEquals(2, stream.lastSequence());
        List<EventEntry> all = stream.after(0, 100);
        assertEquals(2, all.size());
        assertEquals(1, all.get(0).sequence());
        assertEquals("String", all.get(0).type());
        assertEquals("first", all.get(0).payload());
        List<EventEntry> tail = stream.after(1, 100);
        assertEquals(1, tail.size());
        assertEquals("second", tail.get(0).payload());
    }

    @Test
    void awaitNextBlocksUntilEventArrives() throws Exception {
        DefaultEventBus bus = new DefaultEventBus();
        DefaultEventStream stream = new DefaultEventStream(bus, 100);
        AtomicReference<Long> observed = new AtomicReference<>();
        Thread waiter = new Thread(() -> observed.set(stream.awaitNext(0, 5000)));
        waiter.start();
        Thread.sleep(150);
        bus.publish("wake");
        waiter.join(3000);
        assertFalse(waiter.isAlive());
        assertEquals(1L, observed.get());
    }

    @Test
    void awaitNextTimesOutAtCurrentSequence() {
        DefaultEventBus bus = new DefaultEventBus();
        DefaultEventStream stream = new DefaultEventStream(bus, 100);
        long start = System.currentTimeMillis();
        long sequence = stream.awaitNext(0, 150);
        assertTrue(System.currentTimeMillis() - start >= 120);
        assertEquals(0L, sequence);
    }

    @Test
    void capacityEvictsOldestEntries() {
        DefaultEventBus bus = new DefaultEventBus();
        DefaultEventStream stream = new DefaultEventStream(bus, 5);
        for (int i = 0; i < 10; i++) {
            bus.publish("e" + i);
        }
        assertEquals(10, stream.lastSequence());
        List<EventEntry> entries = stream.after(0, 100);
        assertEquals(5, entries.size());
        assertEquals(6, entries.get(0).sequence());
        assertEquals("e5", entries.get(0).payload());
    }
}
