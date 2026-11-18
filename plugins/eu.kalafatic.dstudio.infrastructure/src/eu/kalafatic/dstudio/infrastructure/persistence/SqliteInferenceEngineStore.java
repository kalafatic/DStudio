package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor;
import eu.kalafatic.dstudio.core.store.InferenceEngineStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite implementation of InferenceEngineStore (Phase 6).
 */
public class SqliteInferenceEngineStore implements InferenceEngineStore {

    private final Connection connection;
    private final SecretCodec codec;

    public SqliteInferenceEngineStore(Connection connection, SecretCodec codec) {
        this.connection = connection;
        this.codec = codec;
    }

    @Override
    public void saveDescriptor(InferenceEngineDescriptor descriptor) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO inference_engines (id, name, type, enabled, status, "
                        + "status_message, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, "
                        + "type=excluded.type, enabled=excluded.enabled, "
                        + "status=excluded.status, status_message=excluded.status_message, "
                        + "updated_at=excluded.updated_at")) {
            ps.setString(1, descriptor.getId());
            ps.setString(2, descriptor.getName());
            ps.setString(3, descriptor.getType());
            ps.setInt(4, descriptor.isEnabled() ? 1 : 0);
            ps.setString(5, descriptor.getStatus().name());
            ps.setString(6, descriptor.getStatusMessage());
            ps.setString(7, descriptor.getCreatedAt().toString());
            ps.setString(8, descriptor.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save inference engine descriptor: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDescriptor(String engineId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM inference_engines WHERE id = ?")) {
            ps.setString(1, engineId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete inference engine descriptor: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<InferenceEngineDescriptor> findDescriptor(String engineId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, type, enabled, status, status_message, created_at, "
                        + "updated_at FROM inference_engines WHERE id = ?")) {
            ps.setString(1, engineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load inference engine descriptor: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public List<InferenceEngineDescriptor> listDescriptors() {
        List<InferenceEngineDescriptor> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, type, enabled, status, status_message, created_at, "
                        + "updated_at FROM inference_engines ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list inference engine descriptors: "
                    + e.getMessage(), e);
        }
        return list;
    }

    private InferenceEngineDescriptor map(ResultSet rs) throws Exception {
        return new InferenceEngineDescriptor(rs.getString("id"), rs.getString("name"),
                rs.getString("type"), rs.getInt("enabled") == 1,
                InferenceEngineDescriptor.EngineStatus.valueOf(rs.getString("status")),
                rs.getString("status_message"), Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }

    @Override
    public void saveSelectedEngineId(String engineId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO inference_engine_state (key, value) "
                        + "VALUES ('selected_engine_id', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, engineId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save selected engine id: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> findSelectedEngineId() {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM inference_engine_state "
                        + "WHERE key = 'selected_engine_id'");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load selected engine id: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void clearSelectedEngineId() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM inference_engine_state WHERE key = 'selected_engine_id'")) {
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear selected engine id: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void saveConfiguration(String engineId, Map<String, String> configuration) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO inference_engine_config (engine_id, config_json) VALUES (?, ?) "
                        + "ON CONFLICT(engine_id) DO UPDATE SET config_json=excluded.config_json")) {
            ps.setString(1, engineId);
            ps.setString(2, JsonSupport.writeStringMap(codec.encodeMap(configuration)));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save engine configuration: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> findConfiguration(String engineId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT config_json FROM inference_engine_config WHERE engine_id = ?")) {
            ps.setString(1, engineId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? codec.decodeMap(JsonSupport.readStringMap(rs.getString(1)))
                        : Map.of();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load engine configuration: "
                    + e.getMessage(), e);
        }
    }
}
