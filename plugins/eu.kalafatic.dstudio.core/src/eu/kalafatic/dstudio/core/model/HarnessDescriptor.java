package eu.kalafatic.dstudio.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class HarnessDescriptor {

    public enum HarnessStatus { UNKNOWN, CONNECTED, DISCONNECTED, ERROR }

    private final String id;
    private final String name;
    private final String type;
    private final List<String> capabilities;
    private volatile boolean enabled;
    private volatile HarnessStatus status;
    private volatile String statusMessage;
    private volatile Instant createdAt;
    private volatile Instant updatedAt;

    public HarnessDescriptor(String id, String name, String type, List<String> capabilities,
            boolean enabled, HarnessStatus status, String statusMessage, Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.capabilities = new ArrayList<>(capabilities == null ? List.of() : capabilities);
        this.enabled = enabled;
        this.status = status;
        this.statusMessage = statusMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public List<String> getCapabilities() { return List.copyOf(capabilities); }
    public boolean isEnabled() { return enabled; }
    public HarnessStatus getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEnabled(boolean newEnabled) {
        this.enabled = newEnabled;
        this.updatedAt = Instant.now();
    }

    public void setStatus(HarnessStatus newStatus, String message) {
        this.status = newStatus;
        this.statusMessage = message == null ? "" : message;
        this.updatedAt = Instant.now();
    }
}
