package eu.kalafatic.dstudio.teip.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import eu.kalafatic.dstudio.core.event.EventEntry;
import eu.kalafatic.dstudio.core.event.EventStream;
import eu.kalafatic.dstudio.core.model.ExecutionEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global SSE channel ({@code GET /api/events}): live backend events for session and task
 * lifecycle, harness execution, inference streaming, tool activity, progress and errors.
 *
 * <p>Reconnection contract: the client passes the last seen sequence via
 * {@code ?afterSequence=N} (or the {@code Last-Event-ID} header); buffered events are
 * replayed first, then the stream continues live. The stream is a convenience channel,
 * not a durable record — clients must reconcile against the authoritative REST state
 * after reconnecting.</p>
 *
 * <p>Authentication is identical to the rest of the API surface (bearer token or
 * explicit dev-mode loopback opt-out).</p>
 */
public class EventsSseHandler implements HttpHandler {

    public static final String PATH = "/api/events";
    static final long HEARTBEAT_MILLIS = 15_000;
    static final long POLL_MILLIS = 1_000;
    static final int REPLAY_LIMIT = 1_000;

    private final EventStream eventStream;
    private final boolean devModeConfigured;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean shuttingDown;

    public EventsSseHandler(EventStream eventStream, boolean devModeConfigured) {
        this.eventStream = eventStream;
        this.devModeConfigured = devModeConfigured;
    }

    /** Ends every open SSE loop; used during server shutdown for clean stopping. */
    public void shutdown() {
        shuttingDown = true;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ApiAuth.authenticate(exchange, devModeConfigured);
        } catch (ApiAuth.ApiAuthException e) {
            respond(exchange, 401, errorMap(e.getMessage()));
            return;
        }
        long afterSequence = initialSequence(exchange);
        SseSupport.startSse(exchange);
        try (OutputStream out = exchange.getResponseBody()) {
            long lastSequence = afterSequence;
            for (EventEntry entry : eventStream.after(afterSequence, REPLAY_LIMIT)) {
                writeEntry(out, entry);
                lastSequence = entry.sequence();
            }
            out.flush();
            long idleSince = System.currentTimeMillis();
            while (!shuttingDown) {
                long next = eventStream.awaitNext(lastSequence, POLL_MILLIS);
                if (next > lastSequence) {
                    for (EventEntry entry : eventStream.after(lastSequence, REPLAY_LIMIT)) {
                        writeEntry(out, entry);
                        lastSequence = entry.sequence();
                    }
                    idleSince = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - idleSince >= HEARTBEAT_MILLIS) {
                    SseSupport.writeComment(out, "ping");
                    idleSince = System.currentTimeMillis();
                }
            }
            SseSupport.writeComment(out, "bye");
        } catch (IOException ignored) {
            // client disconnected: normal for SSE
        } finally {
            exchange.close();
        }
    }

    private void writeEntry(OutputStream out, EventEntry entry) throws IOException {
        SseSupport.writeEvent(out, String.valueOf(entry.sequence()), entry.type(),
                mapper.writeValueAsString(wirePayload(entry.payload())));
    }

    /** Wire-safe payload: pre-JavaTime-module Jackson cannot serialize Instant fields. */
    private Object wirePayload(Object payload) {
        if (payload instanceof ExecutionEvent event) {
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("sequence", event.sequence());
            wire.put("executionId", event.executionId());
            wire.put("type", event.type().name());
            wire.put("timestamp", event.timestamp().toString());
            wire.put("message", event.message() == null ? "" : event.message());
            wire.put("data", event.data());
            return wire;
        }
        return payload;
    }

    private long initialSequence(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0 && pair.substring(0, equals).equals("afterSequence")) {
                    try {
                        return Long.parseLong(pair.substring(equals + 1));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        String header = exchange.getRequestHeaders().getFirst("Last-Event-ID");
        if (header != null && !header.isBlank()) {
            try {
                return Long.parseLong(header.trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        // no cursor: live stream only, no replay
        return eventStream.lastSequence();
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> body)
            throws IOException {
        byte[] payload = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
        exchange.close();
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", ApiAuth.ERROR_CODE);
        envelope.put("message", message);
        return envelope;
    }
}
