package eu.kalafatic.dstudio.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;

public class SchemaMigrationService {

    public static final int LATEST_VERSION = 4;

    public void migrate(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version ("
                    + "version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create schema_version table: "
                    + e.getMessage(), e);
        }
        int current = currentVersion(connection);
        if (current < 1) {
            applyV1(connection);
        }
        if (current < 2) {
            applyV2(connection);
        }
        if (current < 3) {
            applyV3(connection);
        }
        if (current < 4) {
            applyV4(connection);
        }
    }

    private int currentVersion(Connection connection) {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read schema version: " + e.getMessage(), e);
        }
    }

    private void applyV1(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS harnesses ("
                    + "id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, "
                    + "capabilities TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, "
                    + "status TEXT NOT NULL DEFAULT 'UNKNOWN', "
                    + "status_message TEXT NOT NULL DEFAULT '', "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS harness_state ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS harness_config ("
                    + "harness_id TEXT PRIMARY KEY, config_json TEXT NOT NULL DEFAULT '{}')");
            statement.execute("CREATE TABLE IF NOT EXISTS projects ("
                    + "id TEXT PRIMARY KEY, name TEXT NOT NULL, "
                    + "root_path TEXT NOT NULL UNIQUE)");
            statement.execute("CREATE TABLE IF NOT EXISTS executions ("
                    + "id TEXT PRIMARY KEY, harness_id TEXT NOT NULL, project_id TEXT, "
                    + "idempotency_key TEXT, instruction TEXT, prompt TEXT, model TEXT, "
                    + "agent TEXT, auto_approve INTEGER NOT NULL DEFAULT 0, "
                    + "working_directory TEXT NOT NULL, status TEXT NOT NULL, "
                    + "progress REAL NOT NULL DEFAULT 0, "
                    + "status_message TEXT NOT NULL DEFAULT '', exit_code INTEGER, "
                    + "summary TEXT, error_message TEXT, "
                    + "timed_out INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, "
                    + "started_at TEXT, completed_at TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS execution_events ("
                    + "execution_id TEXT NOT NULL, sequence INTEGER NOT NULL, "
                    + "event_type TEXT NOT NULL, timestamp TEXT NOT NULL, "
                    + "message TEXT NOT NULL DEFAULT '', "
                    + "data_json TEXT NOT NULL DEFAULT '{}', "
                    + "PRIMARY KEY (execution_id, sequence))");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_events_exec_seq "
                    + "ON execution_events (execution_id, sequence)");
            statement.execute("CREATE TABLE IF NOT EXISTS execution_idempotency ("
                    + "idempotency_key TEXT PRIMARY KEY, execution_id TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS reviews ("
                    + "id TEXT PRIMARY KEY, execution_id TEXT NOT NULL, "
                    + "state TEXT NOT NULL DEFAULT 'OPEN')");
            statement.execute("CREATE TABLE IF NOT EXISTS review_files ("
                    + "review_id TEXT NOT NULL, path TEXT NOT NULL, "
                    + "diff TEXT NOT NULL DEFAULT '', "
                    + "decision TEXT NOT NULL DEFAULT 'PENDING', "
                    + "PRIMARY KEY (review_id, path))");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_version (version, applied_at) VALUES (1, ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply schema migration V1: "
                    + e.getMessage(), e);
        }
    }

    /**
     * V2 (Phase 6): inference-engine registry tables — engines, selected engine state and
     * per-engine configuration. Inference engines (ENR default, Ollama alternative) are
     * managed independently from coding harnesses.
     */
    private void applyV2(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS inference_engines ("
                    + "id TEXT PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, "
                    + "enabled INTEGER NOT NULL DEFAULT 1, "
                    + "status TEXT NOT NULL DEFAULT 'UNKNOWN', "
                    + "status_message TEXT NOT NULL DEFAULT '', "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS inference_engine_state ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS inference_engine_config ("
                    + "engine_id TEXT PRIMARY KEY, config_json TEXT NOT NULL DEFAULT '{}')");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_version (version, applied_at) VALUES (2, ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply schema migration V2: "
                    + e.getMessage(), e);
        }
    }

    /**
     * V3 (Phase 7): persisted direct-inference sessions — multi-turn conversation state
     * managed by the backend and shared by all clients (RCP, Web IDE, TEIP consumers).
     */
    private void applyV3(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS inference_sessions ("
                    + "id TEXT PRIMARY KEY, engine_id TEXT NOT NULL, model TEXT NOT NULL, "
                    + "system_prompt TEXT NOT NULL DEFAULT '', "
                    + "status TEXT NOT NULL DEFAULT 'OPEN', "
                    + "status_message TEXT NOT NULL DEFAULT '', "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS inference_messages ("
                    + "id TEXT PRIMARY KEY, session_id TEXT NOT NULL, "
                    + "role TEXT NOT NULL, content TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_inference_messages_session "
                    + "ON inference_messages (session_id)");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_version (version, applied_at) VALUES (3, ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply schema migration V3: "
                    + e.getMessage(), e);
        }
    }

    /**
     * V4 (unified multi-agent control platform): reusable agent profiles with persisted
     * default selection, scope-based execution configurations (GLOBAL/PROJECT/TASK) and
     * immutable per-execution configuration snapshots. The executions table gains the
     * resolved provider/agent/parameter columns.
     */
    private void applyV4(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS agent_profiles ("
                    + "id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, "
                    + "description TEXT NOT NULL DEFAULT '', role TEXT NOT NULL DEFAULT '', "
                    + "system_instructions TEXT NOT NULL DEFAULT '', "
                    + "tool_permissions TEXT NOT NULL DEFAULT '[]', "
                    + "restrictions TEXT NOT NULL DEFAULT '[]', "
                    + "preferred_harness TEXT NOT NULL DEFAULT '', "
                    + "preferred_provider TEXT NOT NULL DEFAULT '', "
                    + "preferred_model TEXT NOT NULL DEFAULT '', "
                    + "execution_parameters TEXT NOT NULL DEFAULT '{}', "
                    + "created_at TEXT NOT NULL, updated_at TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS agent_profile_state ("
                    + "key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS execution_configs ("
                    + "scope TEXT NOT NULL, scope_key TEXT NOT NULL, "
                    + "config_json TEXT NOT NULL DEFAULT '{}', "
                    + "updated_at TEXT NOT NULL, PRIMARY KEY (scope, scope_key))");
            statement.execute("CREATE TABLE IF NOT EXISTS execution_snapshots ("
                    + "execution_id TEXT PRIMARY KEY, config_json TEXT NOT NULL DEFAULT '{}', "
                    + "created_at TEXT NOT NULL)");
            statement.execute("ALTER TABLE executions ADD COLUMN provider_id TEXT NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE executions ADD COLUMN agent_profile_id TEXT NOT NULL DEFAULT ''");
            statement.execute("ALTER TABLE executions ADD COLUMN exec_params_json TEXT NOT NULL DEFAULT '{}'");
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_version (version, applied_at) VALUES (4, ?)")) {
                ps.setString(1, Instant.now().toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply schema migration V4: "
                    + e.getMessage(), e);
        }
    }
}
