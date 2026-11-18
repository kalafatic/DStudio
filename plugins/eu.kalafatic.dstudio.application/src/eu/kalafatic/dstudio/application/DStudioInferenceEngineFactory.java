package eu.kalafatic.dstudio.application;

import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;
import eu.kalafatic.dstudio.core.inference.InferenceEngineFactory;
import java.util.Map;
import java.util.Optional;

/**
 * Application-layer implementation of the inference-engine factory: knows the concrete
 * provider adapters from the harness bundle and creates independently configurable
 * instances of them (multi-instance runtimes — e.g. two Ollama endpoints, two
 * OpenAI-compatible endpoints). Dependency direction preserved: harness adapters are
 * referenced from the application bundle only, never from core or teip.
 */
public class DStudioInferenceEngineFactory implements InferenceEngineFactory {

    @Override
    public Optional<InferenceEngineAdapter> createEngine(String type, String id, String name,
            Map<String, String> configuration) {
        if (type == null) {
            return Optional.empty();
        }
        InferenceEngineAdapter adapter = switch (type.toUpperCase()) {
            case "ENR" -> new eu.kalafatic.dstudio.harness.enr.EnrInferenceEngineAdapter(
                    id, name);
            case "OLLAMA" -> new eu.kalafatic.dstudio.harness.ollama
                    .OllamaInferenceEngineAdapter(id, name);
            case "OPENAI_COMPATIBLE", "OPENAI" -> new eu.kalafatic.dstudio.harness.openai
                    .OpenAiCompatibleInferenceEngineAdapter(id, name);
            case "ANTHROPIC" -> new eu.kalafatic.dstudio.harness.anthropic
                    .AnthropicInferenceEngineAdapter(id, name);
            default -> null;
        };
        if (adapter == null) {
            return Optional.empty();
        }
        if (configuration != null && !configuration.isEmpty()) {
            adapter.updateConfiguration(configuration);
        }
        return Optional.of(adapter);
    }
}
