package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.model.Project;
import eu.kalafatic.dstudio.core.store.ProjectStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteProjectStore implements ProjectStore {

    private final Connection connection;

    public SqliteProjectStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void saveProject(Project project) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO projects (id, name, root_path) VALUES (?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, "
                + "root_path=excluded.root_path")) {
            ps.setString(1, project.id());
            ps.setString(2, project.name());
            ps.setString(3, project.rootPath());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save project: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeProject(String projectId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM projects WHERE id = ?")) {
            ps.setString(1, projectId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to remove project: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Project> findProject(String projectId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, root_path FROM projects WHERE id = ?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find project: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Project> findProjectByRootPath(String rootPath) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, root_path FROM projects WHERE root_path = ?")) {
            ps.setString(1, rootPath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find project by root path: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public List<Project> listProjects() {
        List<Project> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, root_path FROM projects ORDER BY name");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list projects: " + e.getMessage(), e);
        }
    }

    private Project map(ResultSet rs) throws Exception {
        return new Project(rs.getString("id"), rs.getString("name"),
                rs.getString("root_path"));
    }
}
