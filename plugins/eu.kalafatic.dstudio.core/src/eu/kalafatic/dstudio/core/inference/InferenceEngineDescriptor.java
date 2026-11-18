package eu.kalafatic.dstudio.core.inference;

import java.time.Instant;
import java.util.Objects;

/**
 * Registry entry describing a registered inference engine. Mirrors the harness descriptor
 * model: identity, enablement, connection status and timestamps, persisted through
 * {@link eu.kalafatic.dstudio.core.store.InferenceEngineStore}.
 */
public class InferenceEngineDescriptor {

    public enum EngineStatus {
        UNKNOWN, CONNECTED, DISCONNECTED, ERROR
    }

    private final String id;
    private String name;
    private String type;
    private boolean enabled;
    private EngineStatus status;
    private String statusMessage;
    private final Instant createdAt;
    private Instant updatedAt;

    public InferenceEngineDescriptor(String id, String name, String type, boolean enabled,
            EngineStatus status, String statusMessage, Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = name != null ? name : id;
        this.type = type != null ? type : "";
        this.enabled = enabled;
        this.status = status != null ? status : EngineStatus.UNKNOWN;
        this.statusMessage = statusMessage != null ? statusMessage : "";
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : id;
        touch();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type != null ? type : "";
        touch();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        touch();
    }

    public EngineStatus getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatus(EngineStatus status, String message) {
        this.status = status != null ? status : EngineStatus.UNKNOWN;
        this.statusMessage = message != null ? message : "";
        touch();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
