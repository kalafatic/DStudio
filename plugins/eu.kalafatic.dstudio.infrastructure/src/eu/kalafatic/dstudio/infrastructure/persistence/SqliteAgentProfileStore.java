package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.agent.AgentProfile;
import eu.kalafatic.dstudio.core.store.AgentProfileStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite persistence for agent profiles (schema V4, table {@code agent_profiles}) plus the
 * persisted default-profile selection ({@code agent_profile_state}).
 */
public class SqliteAgentProfileStore implements AgentProfileStore {

    private final Connection connection;

    public SqliteAgentProfileStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void saveProfile(AgentProfile profile) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO agent_profiles (id, name, description, role, system_instructions, "
                + "tool_permissions, restrictions, preferred_harness, preferred_provider, "
                + "preferred_model, execution_parameters, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, "
                + "description=excluded.description, role=excluded.role, "
                + "system_instructions=excluded.system_instructions, "
                + "tool_permissions=excluded.tool_permissions, "
                + "restrictions=excluded.restrictions, "
                + "preferred_harness=excluded.preferred_harness, "
                + "preferred_provider=excluded.preferred_provider, "
                + "preferred_model=excluded.preferred_model, "
                + "execution_parameters=excluded.execution_parameters, "
                + "updated_at=excluded.updated_at")) {
            ps.setString(1, profile.getId());
            ps.setString(2, profile.getName());
            ps.setString(3, profile.getDescription());
            ps.setString(4, profile.getRole());
            ps.setString(5, profile.getSystemInstructions());
            ps.setString(6, JsonSupport.writeStringList(profile.getToolPermissions()));
            ps.setString(7, JsonSupport.writeStringList(profile.getRestrictions()));
            ps.setString(8, profile.getPreferredHarnessId());
            ps.setString(9, profile.getPreferredProviderId());
            ps.setString(10, profile.getPreferredModel());
            ps.setString(11, JsonSupport.writeStringMap(profile.getExecutionParameters()));
            ps.setString(12, profile.getCreatedAt().toString());
            ps.setString(13, profile.getUpdatedAt().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save agent profile: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<AgentProfile> findProfile(String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, description, role, system_instructions, tool_permissions, "
                + "restrictions, preferred_harness, preferred_provider, preferred_model, "
                + "execution_parameters, created_at, updated_at FROM agent_profiles WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readProfile(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load agent profile: " + e.getMessage(), e);
        }
    }

    @Override
    public List<AgentProfile> listProfiles() {
        List<AgentProfile> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, description, role, system_instructions, tool_permissions, "
                + "restrictions, preferred_harness, preferred_provider, preferred_model, "
                + "execution_parameters, created_at, updated_at FROM agent_profiles "
                + "ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readProfile(rs));
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list agent profiles: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteProfile(String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM agent_profiles WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete agent profile: " + e.getMessage(), e);
        }
    }

    @Override
    public void saveDefaultProfileId(String profileId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO agent_profile_state (key, value) VALUES ('default', ?) "
                + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, profileId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save default agent profile: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> findDefaultProfileId() {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT value FROM agent_profile_state WHERE key = 'default'")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load default agent profile: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void clearDefaultProfileId() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM agent_profile_state WHERE key = 'default'")) {
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear default agent profile: "
                    + e.getMessage(), e);
        }
    }

    private AgentProfile readProfile(ResultSet rs) throws Exception {
        return new AgentProfile(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("role"),
                rs.getString("system_instructions"),
                JsonSupport.readStringList(rs.getString("tool_permissions")),
                JsonSupport.readStringList(rs.getString("restrictions")),
                rs.getString("preferred_harness"),
                rs.getString("preferred_provider"),
                rs.getString("preferred_model"),
                JsonSupport.readStringMap(rs.getString("execution_parameters")),
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("updated_at")));
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? Instant.now() : Instant.parse(value);
    }
}
