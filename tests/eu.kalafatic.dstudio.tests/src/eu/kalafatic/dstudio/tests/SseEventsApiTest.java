package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.event.DefaultEventBus;
import eu.kalafatic.dstudio.core.event.DefaultEventStream;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.teip.server.DStudioServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 7 (S4): the /api/events SSE channel must deliver live events with sequence ids,
 * support cursor-based reconnection replay and enforce the shared API authentication.
 */
class SseEventsApiTest {

    private DefaultEventBus eventBus;
    private DefaultEventStream eventStream;
    private DStudioServer server;
    private int port;

    @BeforeEach
    void setUp() {
        eventBus = new DefaultEventBus();
        eventStream = new DefaultEventStream(eventBus, 100);
        server = new DStudioServer("127.0.0.1", 0, null, null, null,
                (exchange) -> exchange.close(), true, eventStream, null, null);
        server.start();
        port = server.getBoundPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void liveEventsDeliveredWithSequenceIds() throws Exception {
        SseConnection connection = open("/api/events");
        assertEquals(200, connection.statusCode());
        try {
            eventBus.publish(new ExecutionEvent(1, "task-1",
                    ExecutionEventType.EXECUTION_QUEUED, Instant.now(), "queued", Map.of()));
            List<String> frames = connection.readUntilFrame(10_000);
            assertTrue(frames.stream().anyMatch(l -> l.startsWith("event: ExecutionEvent")),
                    "frames: " + frames);
            assertTrue(frames.stream().anyMatch(l -> l.startsWith("data:")
                            && l.contains("EXECUTION_QUEUED")),
                    "frames: " + frames);
            assertTrue(frames.stream().anyMatch(l -> l.startsWith("id: ")));
        } finally {
            connection.close();
        }
    }

    @Test
    void reconnectWithCursorReplaysMissedEvents() throws Exception {
        // two events happen while no client is connected
        eventBus.publish(new ExecutionEvent(1, "task-a", ExecutionEventType.EXECUTION_QUEUED,
                Instant.now(), "a", Map.of()));
        eventBus.publish(new ExecutionEvent(2, "task-a", ExecutionEventType.EXECUTION_STARTED,
                Instant.now(), "b", Map.of()));

        SseConnection reconnected = open("/api/events?afterSequence=1");
        assertEquals(200, reconnected.statusCode());
        try {
            List<String> frames = reconnected.readUntilFrame(10_000);
            assertTrue(frames.stream().anyMatch(l -> l.startsWith("id: 2")
                    || l.contains("EXECUTION_STARTED")), "frames: " + frames);
            // the already-seen first event must not be replayed
            assertFalse(frames.stream().anyMatch(l -> l.startsWith("id: 1")),
                    "frames: " + frames);
            // live continuation arrives on the same connection
            eventBus.publish(new ExecutionEvent(3, "task-a", ExecutionEventType.EXECUTION_LOG,
                    Instant.now(), "c", Map.of()));
            List<String> more = reconnected.readUntilLine("id: 3", 10_000);
            assertTrue(more.stream().anyMatch(l -> l.equals("id: 3")), "frames: " + more);
        } finally {
            reconnected.close();
        }
    }

    @Test
    void loopbackSseStaysOpenWhenTokenConfigured() throws Exception {
        System.setProperty("dstudio.teip.auth.token", "sse-secret-token");
        try {
            DStudioServer secured = new DStudioServer("127.0.0.1", 0, null, null, null,
                    (exchange) -> exchange.close(), false, eventStream, null, null);
            secured.start();
            try {
                int securedPort = secured.getBoundPort();
                // Current-phase policy: loopback clients are local-trusted.
                HttpURLConnection open = rawOpen(securedPort, "/api/events");
                assertEquals(200, open.getResponseCode(),
                        "loopback SSE must not require the remote token");
                open.disconnect();
            } finally {
                secured.stop();
            }
        } finally {
            System.clearProperty("dstudio.teip.auth.token");
        }
    }

    private SseConnection open(String path) throws Exception {
        HttpURLConnection connection = rawOpen(port, path);
        return new SseConnection(connection);
    }

    private HttpURLConnection rawOpen(int targetPort, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + targetPort + path).toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Accept", "text/event-stream");
        return connection;
    }

    private static final class SseConnection {
        private final HttpURLConnection connection;
        private final BufferedReader reader;
        private int statusCode;

        SseConnection(HttpURLConnection connection) throws Exception {
            this.connection = connection;
            this.statusCode = connection.getResponseCode();
            this.reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
        }

        int statusCode() {
            return statusCode;
        }

        List<String> readUntilFrame(long timeoutMillis) throws Exception {
            return readUntilLine(null, timeoutMillis);
        }

        List<String> readUntilLine(String wanted, long timeoutMillis) throws Exception {
            List<String> lines = new ArrayList<>();
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                lines.add(line);
                if ((wanted == null && line.startsWith("data:"))
                        || (wanted != null && line.equals(wanted))) {
                    return lines;
                }
            }
            return lines;
        }

        void close() throws Exception {
            connection.disconnect();
        }
    }
}
