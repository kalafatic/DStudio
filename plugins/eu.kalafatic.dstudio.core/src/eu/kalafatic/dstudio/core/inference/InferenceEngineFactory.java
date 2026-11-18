package eu.kalafatic.dstudio.core.inference;

import java.util.Map;
import java.util.Optional;

/**
 * Creates inference-engine adapter instances by provider type. The core registry is
 * provider-agnostic; concrete adapters (ENR, Ollama, OpenAI-compatible, Anthropic, …)
 * live in the harness bundle, so the factory is implemented at the application layer
 * (which may depend on harness) and injected where the core SPI is consumed. Enables
 * multiple registered instances of the same provider type (multi-instance runtimes).
 */
public interface InferenceEngineFactory {

    /** Returns a configured engine instance for the type, or empty for unknown types. */
    Optional<InferenceEngineAdapter> createEngine(String type, String id, String name,
            Map<String, String> configuration);
}
