package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.core.store.HarnessStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SqliteHarnessStore implements HarnessStore {

    private final Connection connection;
    private final SecretCodec codec;

    public SqliteHarnessStore(Connection connection, SecretCodec codec) {
        this.connection = connection;
        this.codec = codec;
    }

    @Override
    public void saveDescriptor(HarnessDescriptor descriptor) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO harnesses (id, name, type, capabilities, enabled, status, "
                + "status_message, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, type=excluded.type, "
                + "capabilities=excluded.capabilities, enabled=excluded.enabled, "
                + "status=excluded.status, status_message=excluded.status_message, "
                + "updated_at=excluded.updated_at")) {
            ps.setString(1, descriptor.getId());
            ps.setString(2, descriptor.getName());
            ps.setString(3, descriptor.getType());
            ps.setString(4, String.join(",", descriptor.getCapabilities()));
            ps.setInt(5, descriptor.isEnabled() ? 1 : 0);
            ps.setString(6, descriptor.getStatus().name());
            ps.setString(7, descriptor.getStatusMessage());
            ps.setString(8, descriptor.getCreatedAt().toString());
            ps.setString(9, descriptor.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save harness descriptor: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDescriptor(String harnessId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM harnesses WHERE id = ?")) {
            ps.setString(1, harnessId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete harness descriptor: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<HarnessDescriptor> findDescriptor(String harnessId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, type, capabilities, enabled, status, status_message, "
                + "created_at, updated_at FROM harnesses WHERE id = ?")) {
            ps.setString(1, harnessId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find harness descriptor: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public List<HarnessDescriptor> listDescriptors() {
        List<HarnessDescriptor> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, type, capabilities, enabled, status, status_message, "
                + "created_at, updated_at FROM harnesses ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list harness descriptors: "
                    + e.getMessage(), e);
        }
    }

    private HarnessDescriptor map(ResultSet rs) throws Exception {
        String capabilities = rs.getString("capabilities");
        List<String> caps = capabilities == null || capabilities.isBlank() ? List.of()
                : Arrays.asList(capabilities.split(","));
        return new HarnessDescriptor(rs.getString("id"), rs.getString("name"),
                rs.getString("type"), caps, rs.getInt("enabled") == 1,
                HarnessDescriptor.HarnessStatus.valueOf(rs.getString("status")),
                rs.getString("status_message"), Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }

    @Override
    public void saveSelectedHarnessId(String harnessId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO harness_state (key, value) VALUES ('selected_harness_id', ?) "
                + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, harnessId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save selected harness id: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> findSelectedHarnessId() {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM harness_state WHERE key = 'selected_harness_id'");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find selected harness id: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void clearSelectedHarnessId() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM harness_state WHERE key = 'selected_harness_id'")) {
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear selected harness id: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void saveConfiguration(String harnessId, Map<String, String> configuration) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO harness_config (harness_id, config_json) VALUES (?, ?) "
                + "ON CONFLICT(harness_id) DO UPDATE SET config_json=excluded.config_json")) {
            ps.setString(1, harnessId);
            ps.setString(2, JsonSupport.writeStringMap(codec.encodeMap(configuration)));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save harness configuration: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> findConfiguration(String harnessId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT config_json FROM harness_config WHERE harness_id = ?")) {
            ps.setString(1, harnessId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? codec.decodeMap(JsonSupport.readStringMap(rs.getString(1)))
                        : Map.of();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find harness configuration: "
                    + e.getMessage(), e);
        }
    }
}
