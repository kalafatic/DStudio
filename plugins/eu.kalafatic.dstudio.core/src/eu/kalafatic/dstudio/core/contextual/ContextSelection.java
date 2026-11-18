package eu.kalafatic.dstudio.core.contextual;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable evaluation input for contextual actions (Darwin Family Contextual Action
 * Contract v1.0, section 2). Built per selection; providers compute availability
 * exclusively from this snapshot — never from global UI state.
 */
public record ContextSelection(
        String contractVersion,
        String application,
        String surface,
        String objectType,
        String objectId,
        String objectLabel,
        Map<String, String> objectState,
        boolean multiSelect,
        String projectId,
        String sessionId,
        String harnessId,
        String providerId,
        String modelId,
        String agentId,
        Set<String> capabilities,
        Set<String> permissions,
        String user) {

    public static final String APPLICATION_DSTUDIO = "DSTUDIO";
    public static final String SURFACE_RCP = "RCP";
    public static final String SURFACE_WEB = "WEB";

    public ContextSelection {
        contractVersion = contractVersion == null ? ContextActionIds.CONTRACT_VERSION
                : contractVersion;
        objectState = objectState == null ? Map.of() : Map.copyOf(objectState);
        capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
        permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
    }

    public String state(String key) {
        return objectState.get(key);
    }

    public static Builder builder(String application, String surface, String objectType) {
        return new Builder(application, surface, objectType);
    }

    public static final class Builder {
        private final String application;
        private final String surface;
        private final String objectType;
        private String objectId = "";
        private String objectLabel = "";
        private final Map<String, String> objectState = new LinkedHashMap<>();
        private boolean multiSelect = false;
        private String projectId = "";
        private String sessionId = "";
        private String harnessId = "";
        private String providerId = "";
        private String modelId = "";
        private String agentId = "";
        private final Set<String> capabilities = new LinkedHashSet<>();
        private final Set<String> permissions = new LinkedHashSet<>();
        private String user = "";

        public Builder(String application, String surface, String objectType) {
            this.application = application;
            this.surface = surface;
            this.objectType = objectType;
        }

        public Builder object(String id, String label) {
            this.objectId = id == null ? "" : id;
            this.objectLabel = label == null ? "" : label;
            return this;
        }

        public Builder state(String key, Object value) {
            if (value != null) {
                objectState.put(key, String.valueOf(value));
            }
            return this;
        }

        public Builder multiSelect(boolean value) {
            this.multiSelect = value;
            return this;
        }

        public Builder projectId(String value) { this.projectId = nz(value); return this; }
        public Builder sessionId(String value) { this.sessionId = nz(value); return this; }
        public Builder harnessId(String value) { this.harnessId = nz(value); return this; }
        public Builder providerId(String value) { this.providerId = nz(value); return this; }
        public Builder modelId(String value) { this.modelId = nz(value); return this; }
        public Builder agentId(String value) { this.agentId = nz(value); return this; }

        public Builder capabilities(Set<String> value) {
            if (value != null) {
                capabilities.addAll(value);
            }
            return this;
        }

        public Builder capabilities(java.util.Collection<String> value) {
            if (value != null) {
                capabilities.addAll(value);
            }
            return this;
        }

        public Builder capability(String value) {
            if (value != null && !value.isBlank()) {
                capabilities.add(value);
            }
            return this;
        }

        public Builder permissions(Set<String> value) {
            if (value != null) {
                permissions.addAll(value);
            }
            return this;
        }

        public Builder user(String value) { this.user = nz(value); return this; }

        private static String nz(String value) {
            return value == null ? "" : value;
        }

        public ContextSelection build() {
            return new ContextSelection(ContextActionIds.CONTRACT_VERSION, application,
                    surface, objectType, objectId, objectLabel, objectState, multiSelect,
                    projectId, sessionId, harnessId, providerId, modelId, agentId,
                    capabilities, permissions, user);
        }
    }
}
