package eu.kalafatic.dstudio.core.inference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import eu.kalafatic.dstudio.core.store.InferenceEngineStore;

/**
 * Default inference-engine registry backed by {@link InferenceEngineStore} (SQLite).
 */
public class DefaultInferenceEngineRegistry implements InferenceEngineRegistry {

    private final InferenceEngineStore store;
    private final Map<String, InferenceEngineAdapter> adapters = new ConcurrentHashMap<>();
    private final Map<String, InferenceEngineDescriptor> descriptors = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> configurations = new ConcurrentHashMap<>();
    private volatile String selectedEngineId;

    public DefaultInferenceEngineRegistry(InferenceEngineStore store) {
        this.store = store;
        for (InferenceEngineDescriptor descriptor : store.listDescriptors()) {
            descriptors.put(descriptor.getId(), descriptor);
            Map<String, String> config = store.findConfiguration(descriptor.getId());
            if (!config.isEmpty()) {
                configurations.put(descriptor.getId(), new ConcurrentHashMap<>(config));
            }
        }
        this.selectedEngineId = store.findSelectedEngineId().orElse("");
    }

    @Override
    public void registerEngine(InferenceEngineAdapter adapter) {
        adapters.put(adapter.getEngineId(), adapter);
        InferenceEngineDescriptor descriptor = descriptors.get(adapter.getEngineId());
        if (descriptor == null) {
            descriptor = new InferenceEngineDescriptor(adapter.getEngineId(),
                    adapter.getEngineName(), adapter.getEngineType(), true,
                    InferenceEngineDescriptor.EngineStatus.UNKNOWN, "",
                    java.time.Instant.now(), java.time.Instant.now());
            descriptors.put(adapter.getEngineId(), descriptor);
        } else {
            descriptor.setStatus(InferenceEngineDescriptor.EngineStatus.UNKNOWN, "");
        }
        Map<String, String> storedConfig = configurations.get(adapter.getEngineId());
        if (storedConfig != null && !storedConfig.isEmpty()) {
            adapter.updateConfiguration(storedConfig);
        }
        store.saveDescriptor(descriptor);
    }

    @Override
    public void unregisterEngine(String engineId) {
        adapters.remove(engineId);
    }

    @Override
    public void removeEngine(String engineId) {
        adapters.remove(engineId);
        descriptors.remove(engineId);
        configurations.remove(engineId);
        if (engineId.equals(selectedEngineId)) {
            selectedEngineId = "";
            store.clearSelectedEngineId();
        }
        store.deleteDescriptor(engineId);
    }

    @Override
    public List<InferenceEngineDescriptor> listEngines() {
        return new ArrayList<>(descriptors.values());
    }

    @Override
    public Optional<InferenceEngineDescriptor> findEngine(String engineId) {
        return Optional.ofNullable(descriptors.get(engineId));
    }

    @Override
    public Optional<InferenceEngineAdapter> findAdapter(String engineId) {
        return Optional.ofNullable(adapters.get(engineId));
    }

    @Override
    public void selectDefaultEngine(String engineId) {
        if (engineId == null || engineId.isBlank()) {
            this.selectedEngineId = "";
            store.clearSelectedEngineId();
            return;
        }
        InferenceEngineDescriptor descriptor = descriptors.get(engineId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown inference engine: " + engineId);
        }
        this.selectedEngineId = engineId;
        store.saveSelectedEngineId(engineId);
    }

    @Override
    public Optional<String> getSelectedDefaultEngineId() {
        return selectedEngineId.isBlank() ? Optional.empty()
                : Optional.of(selectedEngineId);
    }

    @Override
    public Optional<InferenceEngineAdapter> findSelectedDefaultAdapter() {
        Optional<String> id = getSelectedDefaultEngineId();
        if (id.isEmpty()) {
            return Optional.empty();
        }
        InferenceEngineDescriptor descriptor = descriptors.get(id.get());
        InferenceEngineAdapter adapter = adapters.get(id.get());
        if (descriptor == null || !descriptor.isEnabled() || adapter == null) {
            return Optional.empty();
        }
        return Optional.of(adapter);
    }

    @Override
    public void setEngineEnabled(String engineId, boolean enabled) {
        InferenceEngineDescriptor descriptor = descriptors.get(engineId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown inference engine: " + engineId);
        }
        descriptor.setEnabled(enabled);
        store.saveDescriptor(descriptor);
    }

    @Override
    public void updateConfiguration(String engineId, Map<String, String> configuration) {
        InferenceEngineDescriptor descriptor = descriptors.get(engineId);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown inference engine: " + engineId);
        }
        configurations.put(engineId, new ConcurrentHashMap<>(configuration));
        store.saveConfiguration(engineId, configuration);
        InferenceEngineAdapter adapter = adapters.get(engineId);
        if (adapter != null) {
            adapter.updateConfiguration(configuration);
        }
        store.saveDescriptor(descriptor);
    }

    @Override
    public Map<String, String> getConfiguration(String engineId) {
        InferenceEngineAdapter adapter = adapters.get(engineId);
        if (adapter != null) {
            return adapter.getConnectionConfiguration();
        }
        return configurations.getOrDefault(engineId, Map.of());
    }

    @Override
    public InferenceEngineAdapter.InferenceConnectionTestResult testConnection(String engineId) {
        InferenceEngineAdapter adapter = adapters.get(engineId);
        if (adapter == null) {
            return InferenceEngineAdapter.InferenceConnectionTestResult.error(
                    "Unknown inference engine: " + engineId);
        }
        InferenceEngineAdapter.InferenceConnectionTestResult result = adapter.testConnection();
        InferenceEngineDescriptor descriptor = descriptors.get(engineId);
        if (descriptor != null) {
            descriptor.setStatus(result.success()
                    ? InferenceEngineDescriptor.EngineStatus.CONNECTED
                    : InferenceEngineDescriptor.EngineStatus.DISCONNECTED, result.message());
            store.saveDescriptor(descriptor);
        }
        return result;
    }
}
