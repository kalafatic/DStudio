package eu.kalafatic.dstudio.core.inference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of inference engines (Phase 6, WP-6.7). Product-local: engines are registered,
 * enabled, configured and selected here; ENR is the default family engine, alternatives
 * (Ollama, future providers) remain independently selectable. No cross-product shared
 * service is introduced.
 */
public interface InferenceEngineRegistry {

    void registerEngine(InferenceEngineAdapter adapter);

    void unregisterEngine(String engineId);

    /**
     * Permanently removes an engine instance (memory + persisted descriptor/config +
     * selection state). Used by runtime-instance administration.
     */
    void removeEngine(String engineId);

    List<InferenceEngineDescriptor> listEngines();

    Optional<InferenceEngineDescriptor> findEngine(String engineId);

    Optional<InferenceEngineAdapter> findAdapter(String engineId);

    void selectDefaultEngine(String engineId);

    Optional<String> getSelectedDefaultEngineId();

    Optional<InferenceEngineAdapter> findSelectedDefaultAdapter();

    void setEngineEnabled(String engineId, boolean enabled);

    void updateConfiguration(String engineId, Map<String, String> configuration);

    Map<String, String> getConfiguration(String engineId);

    InferenceEngineAdapter.InferenceConnectionTestResult testConnection(String engineId);
}
