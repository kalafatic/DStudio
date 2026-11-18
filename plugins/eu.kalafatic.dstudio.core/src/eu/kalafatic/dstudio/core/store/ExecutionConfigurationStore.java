package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.config.ExecutionConfiguration;
import java.util.List;
import java.util.Optional;

/**
 * Scope-based execution-configuration persistence: global defaults, project overrides,
 * task overrides and immutable per-execution snapshots. Implemented by the SQLite store in
 * the infrastructure bundle.
 */
public interface ExecutionConfigurationStore {

    void save(String scope, String scopeKey, ExecutionConfiguration configuration);

    Optional<ExecutionConfiguration> find(String scope, String scopeKey);

    void delete(String scope, String scopeKey);

    List<ExecutionConfiguration> list(String scope);

    void saveSnapshot(String executionId, ExecutionConfiguration resolved);

    Optional<ExecutionConfiguration> findSnapshot(String executionId);
}
