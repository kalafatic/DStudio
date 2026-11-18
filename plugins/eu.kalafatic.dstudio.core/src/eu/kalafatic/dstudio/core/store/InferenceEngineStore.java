package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence contract for the inference-engine registry (Phase 6).
 */
public interface InferenceEngineStore {

    void saveDescriptor(InferenceEngineDescriptor descriptor);

    void deleteDescriptor(String engineId);

    Optional<InferenceEngineDescriptor> findDescriptor(String engineId);

    List<InferenceEngineDescriptor> listDescriptors();

    void saveSelectedEngineId(String engineId);

    Optional<String> findSelectedEngineId();

    void clearSelectedEngineId();

    void saveConfiguration(String engineId, Map<String, String> configuration);

    Map<String, String> findConfiguration(String engineId);
}
