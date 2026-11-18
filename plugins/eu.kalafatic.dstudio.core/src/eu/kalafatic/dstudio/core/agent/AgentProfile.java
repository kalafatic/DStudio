package eu.kalafatic.dstudio.core.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reusable agent profile (unified multi-agent dimension, Phase 9). An agent profile is an
 * execution persona: system instructions, role, tool permissions, restrictions and the
 * preferred execution environment (harness / inference engine / model). Profiles are
 * deliberately decoupled from any concrete harness or engine — a profile is compatible with
 * an execution environment, it is never hardwired to one.
 */
public class AgentProfile {

    private final String id;
    private volatile String name;
    private volatile String description;
    private volatile String role;
    private volatile String systemInstructions;
    private volatile List<String> toolPermissions;
    private volatile List<String> restrictions;
    private volatile String preferredHarnessId;
    private volatile String preferredProviderId;
    private volatile String preferredModel;
    private volatile Map<String, String> executionParameters;
    private final Instant createdAt;
    private volatile Instant updatedAt;

    public AgentProfile(String id, String name, String description, String role,
            String systemInstructions, List<String> toolPermissions, List<String> restrictions,
            String preferredHarnessId, String preferredProviderId, String preferredModel,
            Map<String, String> executionParameters, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.role = role == null ? "" : role;
        this.systemInstructions = systemInstructions == null ? "" : systemInstructions;
        this.toolPermissions = new ArrayList<>(toolPermissions == null ? List.of() : toolPermissions);
        this.restrictions = new ArrayList<>(restrictions == null ? List.of() : restrictions);
        this.preferredHarnessId = preferredHarnessId == null ? "" : preferredHarnessId;
        this.preferredProviderId = preferredProviderId == null ? "" : preferredProviderId;
        this.preferredModel = preferredModel == null ? "" : preferredModel;
        this.executionParameters = new LinkedHashMap<>(
                executionParameters == null ? Map.of() : executionParameters);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getRole() { return role; }
    public String getSystemInstructions() { return systemInstructions; }
    public List<String> getToolPermissions() { return List.copyOf(toolPermissions); }
    public List<String> getRestrictions() { return List.copyOf(restrictions); }
    public String getPreferredHarnessId() { return preferredHarnessId; }
    public String getPreferredProviderId() { return preferredProviderId; }
    public String getPreferredModel() { return preferredModel; }
    public Map<String, String> getExecutionParameters() { return Map.copyOf(executionParameters); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String newName, String newDescription, String newRole,
            String newSystemInstructions, List<String> newToolPermissions,
            List<String> newRestrictions, String newPreferredHarnessId,
            String newPreferredProviderId, String newPreferredModel,
            Map<String, String> newExecutionParameters) {
        this.name = newName == null ? "" : newName;
        this.description = newDescription == null ? "" : newDescription;
        this.role = newRole == null ? "" : newRole;
        this.systemInstructions = newSystemInstructions == null ? "" : newSystemInstructions;
        this.toolPermissions = new ArrayList<>(newToolPermissions == null ? List.of() : newToolPermissions);
        this.restrictions = new ArrayList<>(newRestrictions == null ? List.of() : newRestrictions);
        this.preferredHarnessId = newPreferredHarnessId == null ? "" : newPreferredHarnessId;
        this.preferredProviderId = newPreferredProviderId == null ? "" : newPreferredProviderId;
        this.preferredModel = newPreferredModel == null ? "" : newPreferredModel;
        this.executionParameters = new LinkedHashMap<>(
                newExecutionParameters == null ? Map.of() : newExecutionParameters);
        this.updatedAt = Instant.now();
    }

    /** Deep copy with a new id (clone/import support). */
    public AgentProfile copyWithId(String newId) {
        return new AgentProfile(newId, name, description, role, systemInstructions,
                toolPermissions, restrictions, preferredHarnessId, preferredProviderId,
                preferredModel, executionParameters, createdAt, Instant.now());
    }
}
