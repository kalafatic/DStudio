package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.inference.InferenceSession;
import eu.kalafatic.dstudio.core.inference.InferenceSessionMessage;
import eu.kalafatic.dstudio.core.store.InferenceSessionStore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite implementation of InferenceSessionStore (schema V3). */
public class SqliteInferenceSessionStore implements InferenceSessionStore {

    private final Connection connection;

    public SqliteInferenceSessionStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void saveSession(InferenceSession session) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO inference_sessions (id, engine_id, model, system_prompt, "
                        + "status, status_message, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET status=excluded.status, "
                        + "status_message=excluded.status_message, "
                        + "updated_at=excluded.updated_at")) {
            ps.setString(1, session.id());
            ps.setString(2, session.engineId());
            ps.setString(3, session.model());
            ps.setString(4, session.systemPrompt());
            ps.setString(5, session.status().name());
            ps.setString(6, session.statusMessage());
            ps.setString(7, session.createdAt().toString());
            ps.setString(8, session.updatedAt().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save inference session: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Optional<InferenceSession> findSession(String sessionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, engine_id, model, system_prompt, status, status_message, "
                        + "created_at, updated_at FROM inference_sessions WHERE id = ?")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find inference session: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public List<InferenceSession> listSessions() {
        List<InferenceSession> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, engine_id, model, system_prompt, status, status_message, "
                        + "created_at, updated_at FROM inference_sessions ORDER BY rowid");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list inference sessions: "
                    + e.getMessage(), e);
        }
    }

    private InferenceSession map(ResultSet rs) throws Exception {
        return new InferenceSession(rs.getString("id"), rs.getString("engine_id"),
                rs.getString("model"), rs.getString("system_prompt"),
                InferenceSession.Status.valueOf(rs.getString("status")),
                rs.getString("status_message"), Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }

    @Override
    public void addMessage(InferenceSessionMessage message) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO inference_messages (id, session_id, role, content, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, message.id());
            ps.setString(2, message.sessionId());
            ps.setString(3, message.role());
            ps.setString(4, message.content());
            ps.setString(5, message.createdAt().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to add inference message: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public List<InferenceSessionMessage> listMessages(String sessionId) {
        List<InferenceSessionMessage> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, session_id, role, content, created_at FROM inference_messages "
                        + "WHERE session_id = ? ORDER BY rowid")) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new InferenceSessionMessage(rs.getString("id"),
                            rs.getString("session_id"), rs.getString("role"),
                            rs.getString("content"), Instant.parse(rs.getString("created_at"))));
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list inference messages: "
                    + e.getMessage(), e);
        }
    }
}
