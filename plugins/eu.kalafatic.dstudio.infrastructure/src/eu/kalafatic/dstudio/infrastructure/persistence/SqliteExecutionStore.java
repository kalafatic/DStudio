package eu.kalafatic.dstudio.infrastructure.persistence;

import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.core.model.ExecutionStatus;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import eu.kalafatic.dstudio.core.store.ExecutionStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteExecutionStore implements ExecutionStore {

    private final Connection connection;

    public SqliteExecutionStore(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void saveExecution(ExecutionTask task) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO executions (id, harness_id, project_id, idempotency_key, "
                + "instruction, prompt, model, agent, auto_approve, working_directory, "
                + "status, progress, status_message, exit_code, summary, error_message, "
                + "timed_out, created_at, started_at, completed_at, provider_id, "
                + "agent_profile_id, exec_params_json) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bind(ps, task);
            ps.setString(21, task.getProviderId());
            ps.setString(22, task.getAgentProfileId());
            ps.setString(23, JsonSupport.writeStringMap(task.getExecutionParameters()));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save execution: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateExecution(ExecutionTask task) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE executions SET status=?, progress=?, status_message=?, exit_code=?, "
                + "summary=?, error_message=?, timed_out=?, started_at=?, completed_at=? "
                + "WHERE id=?")) {
            ps.setString(1, task.getStatus().name());
            ps.setDouble(2, task.getProgress());
            ps.setString(3, task.getStatusMessage());
            if (task.getExitCode() >= 0) {
                ps.setInt(4, task.getExitCode());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, task.getSummary());
            ps.setString(6, task.getErrorMessage());
            ps.setInt(7, task.isTimedOut() ? 1 : 0);
            ps.setString(8, task.getStartedAt() == null ? null
                    : task.getStartedAt().toString());
            ps.setString(9, task.getCompletedAt() == null ? null
                    : task.getCompletedAt().toString());
            ps.setString(10, task.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update execution: " + e.getMessage(), e);
        }
    }

    private void bind(PreparedStatement ps, ExecutionTask task) throws Exception {
        ps.setString(1, task.getId());
        ps.setString(2, task.getHarnessId());
        ps.setString(3, task.getProjectId());
        ps.setString(4, task.getIdempotencyKey());
        ps.setString(5, task.getInstruction());
        ps.setString(6, task.getPrompt());
        ps.setString(7, task.getModel());
        ps.setString(8, task.getAgent());
        ps.setInt(9, task.isAutoApprove() ? 1 : 0);
        ps.setString(10, task.getWorkingDirectory());
        ps.setString(11, task.getStatus().name());
        ps.setDouble(12, task.getProgress());
        ps.setString(13, task.getStatusMessage());
        if (task.getExitCode() >= 0) {
            ps.setInt(14, task.getExitCode());
        } else {
            ps.setNull(14, java.sql.Types.INTEGER);
        }
        ps.setString(15, task.getSummary());
        ps.setString(16, task.getErrorMessage());
        ps.setInt(17, task.isTimedOut() ? 1 : 0);
        ps.setString(18, task.getCreatedAt().toString());
        ps.setString(19, task.getStartedAt() == null ? null : task.getStartedAt().toString());
        ps.setString(20, task.getCompletedAt() == null ? null : task.getCompletedAt().toString());
    }

    @Override
    public Optional<ExecutionTask> findExecution(String executionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, harness_id, project_id, idempotency_key, instruction, prompt, "
                + "model, agent, auto_approve, working_directory, status, progress, "
                + "status_message, exit_code, summary, error_message, timed_out, created_at, "
                + "started_at, completed_at, provider_id, agent_profile_id, exec_params_json "
                + "FROM executions WHERE id = ?")) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find execution: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ExecutionTask> findExecutionByIdempotencyKey(String idempotencyKey) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT execution_id FROM execution_idempotency WHERE idempotency_key = ?")) {
            ps.setString(1, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? findExecution(rs.getString(1)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to find execution by idempotency key: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void saveIdempotency(String idempotencyKey, String executionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO execution_idempotency (idempotency_key, execution_id, created_at) "
                + "VALUES (?, ?, ?)")) {
            ps.setString(1, idempotencyKey);
            ps.setString(2, executionId);
            ps.setString(3, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save idempotency: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ExecutionTask> listOpenExecutions() {
        return listByStatus("SELECT id FROM executions WHERE status NOT IN "
                + "('COMPLETED','FAILED','CANCELLED','TIMED_OUT') ORDER BY created_at");
    }

    @Override
    public List<ExecutionTask> listExecutions() {
        return listByStatus("SELECT id FROM executions ORDER BY created_at DESC");
    }

    private List<ExecutionTask> listByStatus(String sql) {
        List<ExecutionTask> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                findExecution(rs.getString(1)).ifPresent(result::add);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to list executions: " + e.getMessage(), e);
        }
    }

    private ExecutionTask map(ResultSet rs) throws Exception {
        Instant createdAt = Instant.parse(rs.getString("created_at"));
        ExecutionTask task = new ExecutionTask(rs.getString("id"), rs.getString("harness_id"),
                rs.getString("project_id"), rs.getString("idempotency_key"),
                rs.getString("instruction"), rs.getString("prompt"), rs.getString("model"),
                rs.getString("agent"), rs.getInt("auto_approve") == 1,
                rs.getString("working_directory"),
                nullable(rs, "provider_id"), nullable(rs, "agent_profile_id"),
                JsonSupport.readStringMap(nullable(rs, "exec_params_json")), createdAt);
        ExecutionStatus status = ExecutionStatus.valueOf(rs.getString("status"));
        String startedAt = rs.getString("started_at");
        String completedAt = rs.getString("completed_at");
        String summary = rs.getString("summary");
        String errorMessage = rs.getString("error_message");
        String statusMessage = rs.getString("status_message");
        int exitCode = rs.getInt("exit_code");
        switch (status) {
            case RUNNING -> {
                task.markRunning();
                task.updateProgress(rs.getDouble("progress"), statusMessage);
            }
            case COMPLETED -> task.markCompleted(summary == null ? "" : summary, exitCode);
            case FAILED -> task.markFailed(errorMessage == null ? "" : errorMessage, exitCode);
            case CANCELLED -> task.markCancelled(statusMessage);
            case TIMED_OUT -> task.markTimedOut(statusMessage);
            default -> {
                task.updateProgress(rs.getDouble("progress"), statusMessage);
            }
        }
        return task;
    }

    private static String nullable(ResultSet rs, String column) throws Exception {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    @Override
    public void appendEvent(ExecutionEvent event) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO execution_events (execution_id, sequence, event_type, timestamp, "
                + "message, data_json) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, event.executionId());
            ps.setLong(2, event.sequence());
            ps.setString(3, event.type().name());
            ps.setString(4, event.timestamp().toString());
            ps.setString(5, event.message());
            ps.setString(6, JsonSupport.writeMap(event.data()));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to append event: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ExecutionEvent> loadEvents(String executionId, long afterSequence, int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        List<ExecutionEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sequence, event_type, timestamp, message, data_json "
                + "FROM execution_events WHERE execution_id = ? AND sequence > ? "
                + "ORDER BY sequence ASC LIMIT ?")) {
            ps.setString(1, executionId);
            ps.setLong(2, afterSequence);
            ps.setInt(3, capped);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ExecutionEvent(rs.getLong("sequence"), executionId,
                            ExecutionEventType.valueOf(rs.getString("event_type")),
                            Instant.parse(rs.getString("timestamp")),
                            rs.getString("message"),
                            JsonSupport.readMap(rs.getString("data_json"))));
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load events: " + e.getMessage(), e);
        }
    }

    @Override
    public long nextSequence(String executionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(sequence), 0) FROM execution_events "
                + "WHERE execution_id = ?")) {
            ps.setString(1, executionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read next sequence: "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void clearAll() {
        try (var statement = connection.createStatement()) {
            statement.execute("DELETE FROM execution_events");
            statement.execute("DELETE FROM execution_idempotency");
            statement.execute("DELETE FROM executions");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to clear executions: "
                    + e.getMessage(), e);
        }
    }
}
