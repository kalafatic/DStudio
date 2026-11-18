package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.config.ExecutionConfiguration;
import eu.kalafatic.dstudio.core.store.ExecutionConfigurationStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite persistence for scope-based execution configurations (schema V4, tables
 * {@code execution_configs} and {@code execution_snapshots}). Snapshots are immutable
 * records of the resolved configuration at acceptance time.
 */
public class SqliteExecutionConfigurationStore implements ExecutionConfigurationStore {

    private final Connection connection;

    public SqliteExecutionConfigurationStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void save(String scope, String scopeKey, ExecutionConfiguration configuration) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO execution_configs (scope, scope_key, config_json, updated_at) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(scope, scope_key) DO UPDATE SET "
                + "config_json=excluded.config_json, updated_at=excluded.updated_at")) {
            ps.setString(1, scope);
            ps.setString(2, scopeKey);
            ps.setString(3, writeConfiguration(configuration));
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save execution configuration: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ExecutionConfiguration> find(String scope, String scopeKey) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT config_json FROM execution_configs WHERE scope = ? AND scope_key = ?")) {
            ps.setString(1, scope);
            ps.setString(2, scopeKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readConfiguration(rs.getString(1), scope))
                        : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load execution configuration: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String scope, String scopeKey) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM execution_configs WHERE scope = ? AND scope_key = ?")) {
            ps.setString(1, scope);
            ps.setString(2, scopeKey);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete execution configuration: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public List<ExecutionConfiguration> list(String scope) {
        List<ExecutionConfiguration> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT scope_key, config_json FROM execution_configs WHERE scope = ? "
                + "ORDER BY scope_key")) {
            ps.setString(1, scope);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readConfiguration(rs.getString(2), scope));
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list execution configurations: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void saveSnapshot(String executionId, ExecutionConfiguration resolved) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO execution_snapshots (execution_id, config_json, created_at) "
                + "VALUES (?, ?, ?) "
                + "ON CONFLICT(execution_id) DO UPDATE SET "
                + "config_json=excluded.config_json, created_at=excluded.created_at")) {
            ps.setString(1, executionId);
            ps.setString(2, writeConfiguration(resolved));
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save execution snapshot: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ExecutionConfiguration> findSnapshot(String executionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT config_json FROM execution_snapshots WHERE execution_id = ?")) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readConfiguration(rs.getString(1),
                        ExecutionConfiguration.SOURCE_EXECUTION)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load execution snapshot: "
                    + e.getMessage(), e);
        }
    }

    private static String writeConfiguration(ExecutionConfiguration configuration) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("harnessId", nullToEmpty(configuration.harnessId()));
        data.put("providerId", nullToEmpty(configuration.providerId()));
        data.put("modelId", nullToEmpty(configuration.modelId()));
        data.put("agentProfileId", nullToEmpty(configuration.agentProfileId()));
        data.put("executionParameters", configuration.executionParameters());
        data.put("toolPermissions", configuration.toolPermissions());
        data.put("resourceLimits", configuration.resourceLimits());
        data.put("harnessSettings", configuration.harnessSettings());
        data.put("source", configuration.source());
        return JsonSupport.writeMap(data);
    }

    private static ExecutionConfiguration readConfiguration(String json, String fallbackSource) {
        Map<String, Object> data = JsonSupport.readMap(json);
        String source = data.get("source") == null ? fallbackSource
                : String.valueOf(data.get("source"));
        return new ExecutionConfiguration(
                asString(data.get("harnessId")),
                asString(data.get("providerId")),
                asString(data.get("modelId")),
                asString(data.get("agentProfileId")),
                asStringMap(data.get("executionParameters")),
                asStringList(data.get("toolPermissions")),
                asStringMap(data.get("resourceLimits")),
                asStringMap(data.get("harnessSettings")),
                source);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k),
                    v == null ? "" : String.valueOf(v)));
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            list.forEach(item -> result.add(String.valueOf(item)));
            return result;
        }
        return List.of();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
