package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter.InferenceChatMessage;
import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter.InferenceChatRequest;
import eu.kalafatic.dstudio.harness.openai.OpenAiCompatibleInferenceEngineAdapter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Phase 7 (S3) live evidence: the OpenAI-compatible adapter drives real inference
 * through the OpenAI wire contract served by the locally installed Ollama runtime
 * (http://localhost:11434/v1). Skips silently when Ollama is unavailable, per family
 * test conventions.
 */
class OpenAiCompatibleLiveTest {

    @Test
    @Timeout(420)
    void liveModelListingAgainstOllamaV1() {
        OpenAiCompatibleInferenceEngineAdapter adapter =
                new OpenAiCompatibleInferenceEngineAdapter();
        if (!adapter.testConnection().success()) {
            return;
        }
        var models = adapter.listModels();
        assertFalse(models.isEmpty(), "Ollama /v1 must publish at least one model");
        System.out.println("[OpenAiCompatibleLiveTest] models via /v1: " + models.size());
    }

    @Test
    @Timeout(420)
    void liveBlockingChatAgainstOllamaV1() {
        OpenAiCompatibleInferenceEngineAdapter adapter =
                new OpenAiCompatibleInferenceEngineAdapter();
        if (!adapter.testConnection().success()) {
            return;
        }
        var response = adapter.chat(new InferenceChatRequest(
                "qwen3-coder-30b-32k:latest",
                List.of(InferenceChatMessage.user("Say exactly: OK")), 0.7, 16));
        assertFalse(response.content().isBlank(), "real content expected");
        System.out.println("[OpenAiCompatibleLiveTest] live chat: model=" + response.model()
                + " finish=" + response.finishReason() + " content="
                + response.content().replaceAll("\\s+", " ").trim()
                + " durationMs=" + response.durationMs());
    }

    @Test
    @Timeout(420)
    void liveStreamingChatAgainstOllamaV1() {
        OpenAiCompatibleInferenceEngineAdapter adapter =
                new OpenAiCompatibleInferenceEngineAdapter();
        if (!adapter.testConnection().success()) {
            return;
        }
        StringBuilder out = new StringBuilder();
        AtomicBoolean done = new AtomicBoolean();
        try {
            adapter.streamChat(new InferenceChatRequest("qwen3-coder-30b-32k:latest",
                    List.of(InferenceChatMessage.user("Say exactly: OK")), 0.7, 16),
                    chunk -> {
                        if (chunk.done()) {
                            done.set(true);
                        }
                        out.append(chunk.text());
                    }, () -> false);
        } catch (Exception e) {
            throw new AssertionError("live streaming must not fail: " + e.getMessage(), e);
        }
        assertTrue(done.get(), "stream must terminate with a done chunk");
        assertFalse(out.toString().isBlank(), "stream must carry real content");
        System.out.println("[OpenAiCompatibleLiveTest] live streaming content="
                + out.toString().replaceAll("\\s+", " ").trim());
    }
}
