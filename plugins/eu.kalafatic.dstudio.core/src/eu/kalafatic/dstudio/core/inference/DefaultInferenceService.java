package eu.kalafatic.dstudio.core.inference;

import eu.kalafatic.dstudio.core.event.EventBus;
import eu.kalafatic.dstudio.core.store.InferenceSessionStore;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Default inference service: resolves the engine from the registry (never falls back),
 * persists conversation state, streams through the engine adapter and publishes live
 * {@link InferenceStreamEvent}s on the event bus for all clients.
 */
public class DefaultInferenceService implements InferenceService {

    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final InferenceEngineRegistry registry;
    private final InferenceSessionStore store;
    private final EventBus eventBus;
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public DefaultInferenceService(InferenceEngineRegistry registry,
            InferenceSessionStore store, EventBus eventBus) {
        this.registry = registry;
        this.store = store;
        this.eventBus = eventBus;
    }

    @Override
    public InferenceSession createSession(String engineId, String model, String systemPrompt) {
        return createSession(engineId, model, systemPrompt, null);
    }

    @Override
    public InferenceSession createSession(String engineId, String model, String systemPrompt,
            String title) {
        InferenceEngineAdapter adapter = resolveEngine(engineId);
        String selectedModel = resolveModel(adapter, model);
        InferenceSession session = InferenceSession.of(newSessionId(), adapter.getEngineId(),
                selectedModel, systemPrompt, title);
        store.saveSession(session);
        eventBus.publish(InferenceStreamEvent.created(session));
        return session;
    }

    @Override
    public InferenceSession renameSession(String sessionId, String title) {
        if (title == null || title.isBlank()) {
            throw new InferenceException(InferenceException.Code.VALIDATION_FAILED,
                    "Session title must not be blank");
        }
        InferenceSession current = store.findSession(sessionId).orElseThrow(() ->
                new InferenceException(InferenceException.Code.NOT_FOUND,
                        "Unknown inference session: " + sessionId));
        InferenceSession updated = new InferenceSession(current.id(), current.engineId(),
                current.model(), current.systemPrompt(), current.status(),
                current.statusMessage(), current.createdAt(), Instant.now(), title);
        store.saveSession(updated);
        return updated;
    }

    @Override
    public void deleteSession(String sessionId) {
        store.deleteSession(sessionId);
    }

    @Override
    public List<InferenceSession> listSessions() {
        return store.listSessions();
    }

    @Override
    public Optional<InferenceSession> getSession(String sessionId) {
        return store.findSession(sessionId);
    }

    @Override
    public List<InferenceSessionMessage> getMessages(String sessionId) {
        return store.listMessages(sessionId);
    }

    @Override
    public SendResult sendMessage(String sessionId, String content) {
        StringBuilder assembled = new StringBuilder();
        return run(sessionId, content, chunk -> {
            if (!chunk.text().isEmpty()) {
                assembled.append(chunk.text());
            }
        });
    }

    @Override
    public void streamMessage(String sessionId, String content,
            Consumer<InferenceEngineAdapter.InferenceChatChunk> chunkConsumer) {
        run(sessionId, content, chunkConsumer);
    }

    @Override
    public void cancel(String sessionId) {
        Optional<InferenceSession> session = store.findSession(sessionId);
        if (session.isEmpty() || session.get().status() != InferenceSession.Status.OPEN) {
            return;
        }
        AtomicBoolean flag = cancelFlags.get(sessionId);
        if (flag != null) {
            // an active run observes the flag and finalizes as CANCELLED
            flag.set(true);
            return;
        }
        markCancelled(session.get());
    }

    private SendResult run(String sessionId, String content,
            Consumer<InferenceEngineAdapter.InferenceChatChunk> chunkConsumer) {
        if (content == null || content.isBlank()) {
            throw new InferenceException(InferenceException.Code.VALIDATION_FAILED,
                    "Message content is required");
        }
        InferenceSession session = store.findSession(sessionId)
                .orElseThrow(() -> new InferenceException(InferenceException.Code.NOT_FOUND,
                        "Unknown inference session: " + sessionId));
        if (session.status() != InferenceSession.Status.OPEN) {
            throw new InferenceException(InferenceException.Code.CONFLICT,
                    "Inference session " + sessionId + " is " + session.status()
                            + " and cannot accept messages");
        }
        InferenceEngineAdapter adapter = resolveEngine(session.engineId());
        AtomicBoolean cancelled = cancelFlags.computeIfAbsent(sessionId,
                k -> new AtomicBoolean(false));

        store.addMessage(InferenceSessionMessage.of(sessionId, "user", content));
        List<InferenceSessionMessage> history = store.listMessages(sessionId);
        List<InferenceEngineAdapter.InferenceChatMessage> messages = new ArrayList<>();
        if (!session.systemPrompt().isBlank()) {
            messages.add(InferenceEngineAdapter.InferenceChatMessage
                    .system(session.systemPrompt()));
        }
        for (InferenceSessionMessage message : history) {
            messages.add(new InferenceEngineAdapter.InferenceChatMessage(
                    message.role(), message.content()));
        }

        long start = System.currentTimeMillis();
        StringBuilder streamed = new StringBuilder();
        boolean[] done = {false};
        try {
            adapter.streamChat(new InferenceEngineAdapter.InferenceChatRequest(
                    session.model(), messages, 0.7, DEFAULT_MAX_TOKENS), chunk -> {
                if (!chunk.text().isEmpty()) {
                    streamed.append(chunk.text());
                    eventBus.publish(InferenceStreamEvent.chunk(sessionId,
                            session.engineId(), session.model(), chunk.text()));
                    chunkConsumer.accept(chunk);
                }
                if (chunk.done()) {
                    done[0] = true;
                    chunkConsumer.accept(chunk);
                }
            }, cancelled::get);
        } catch (InferenceException e) {
            throw e;
        } catch (Exception e) {
            if (!cancelled.get()) {
                markFailed(session, e.getMessage());
                throw new InferenceException(InferenceException.Code.INFERENCE_FAILED,
                        "Inference engine " + session.engineId() + " failed: "
                                + e.getMessage(), e);
            }
            // cancelled mid-stream: adapters abort the request with an exception
        } finally {
            cancelFlags.remove(sessionId, cancelled);
        }
        long durationMs = System.currentTimeMillis() - start;

        if (cancelled.get()) {
            if (streamed.length() > 0) {
                store.addMessage(InferenceSessionMessage.of(sessionId, "assistant",
                        streamed.toString()));
            }
            markCancelled(session);
            return new SendResult(streamed.toString(), "cancelled", session.model(),
                    session.engineId(), durationMs);
        }
        if (!done[0]) {
            markFailed(session, "Engine stream ended without completion marker");
            throw new InferenceException(InferenceException.Code.INFERENCE_FAILED,
                    "Inference engine " + session.engineId()
                            + " stream ended without completion marker");
        }
        store.addMessage(InferenceSessionMessage.of(sessionId, "assistant",
                streamed.toString()));
        store.saveSession(withUpdatedAt(session));
        eventBus.publish(InferenceStreamEvent.done(sessionId, session.engineId(),
                session.model(), durationMs));
        return new SendResult(streamed.toString(), "stop", session.model(),
                session.engineId(), durationMs);
    }

    private void markCancelled(InferenceSession session) {
        InferenceSession updated = new InferenceSession(session.id(), session.engineId(),
                session.model(), session.systemPrompt(), InferenceSession.Status.CANCELLED,
                "cancelled by request", session.createdAt(), Instant.now(),
                session.title());
        store.saveSession(updated);
        eventBus.publish(InferenceStreamEvent.cancelled(session.id(), session.engineId(),
                session.model()));
    }

    private void markFailed(InferenceSession session, String message) {
        InferenceSession updated = new InferenceSession(session.id(), session.engineId(),
                session.model(), session.systemPrompt(), InferenceSession.Status.FAILED,
                message == null ? "" : message, session.createdAt(), Instant.now(),
                session.title());
        store.saveSession(updated);
        eventBus.publish(InferenceStreamEvent.error(session.id(), session.engineId(),
                session.model(), message));
    }

    private static InferenceSession withUpdatedAt(InferenceSession session) {
        return new InferenceSession(session.id(), session.engineId(), session.model(),
                session.systemPrompt(), session.status(), session.statusMessage(),
                session.createdAt(), Instant.now(), session.title());
    }

    private InferenceEngineAdapter resolveEngine(String engineId) {
        Optional<InferenceEngineAdapter> adapter;
        if (engineId == null || engineId.isBlank()) {
            adapter = registry.findSelectedDefaultAdapter();
        } else {
            adapter = registry.findAdapter(engineId);
        }
        InferenceEngineAdapter resolved = adapter.orElseThrow(() ->
                new InferenceException(InferenceException.Code.NOT_FOUND,
                        "Unknown inference engine: " + engineId));
        boolean enabled = registry.listEngines().stream()
                .filter(engine -> engine.getId().equals(resolved.getEngineId()))
                .findFirst().map(InferenceEngineDescriptor::isEnabled).orElse(true);
        if (!enabled) {
            throw new InferenceException(InferenceException.Code.CONFLICT,
                    "Inference engine " + resolved.getEngineId() + " is disabled");
        }
        return resolved;
    }

    private String resolveModel(InferenceEngineAdapter adapter, String model) {
        if (model != null && !model.isBlank()) {
            return model;
        }
        String defaultModel = adapter.getConnectionConfiguration()
                .getOrDefault("defaultModel", "");
        if (defaultModel == null || defaultModel.isBlank()) {
            throw new InferenceException(InferenceException.Code.VALIDATION_FAILED,
                    "model is required for engine " + adapter.getEngineId());
        }
        return defaultModel;
    }

    private static String newSessionId() {
        byte[] bytes = new byte[6];
        new SecureRandom().nextBytes(bytes);
        StringBuilder id = new StringBuilder("inf-");
        for (byte b : bytes) {
            id.append(String.format("%02x", b));
        }
        return id.toString();
    }
}
