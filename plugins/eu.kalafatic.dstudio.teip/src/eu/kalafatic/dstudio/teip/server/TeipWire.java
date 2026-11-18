package eu.kalafatic.dstudio.teip.server;

import java.util.List;
import java.util.Map;

public final class TeipWire {

    public static final String PROTOCOL = "TEIP";
    public static final String VERSION = "1.1";
    public static final int MAX_REQUEST_BYTES = 5 * 1024 * 1024;

    private TeipWire() {
    }

    public record Caller(String system, String projectId, String taskId, String operator) {
    }

    public record ContextReference(String type, String path, String description) {
    }

    public record ExecutionRequest(String protocol, String version, String requestId,
            String correlationId, String idempotencyKey, String timestamp, Caller caller,
            String capability, String mode, String model, String instruction,
            List<ContextReference> contextReferences, List<String> grantedToolScopes,
            Map<String, Object> parameters) {
    }

    public record AcceptedResponse(String protocol, String version, String executionId,
            String status, String correlationId, String createdAt,
            Map<String, String> links) {
    }

    public record StatusResponse(String protocol, String version, String executionId,
            String status, String correlationId, String phase, double progress,
            String statusMessage, String createdAt, String startedAt, String updatedAt,
            long lastSequence, Map<String, Object> details) {
    }

    public record Event(String eventId, String executionId, long sequence, String type,
            String timestamp, String correlationId, Map<String, Object> data) {
    }

    public record EventsResponse(String protocol, String version, String executionId,
            List<Event> events, boolean hasMore, long nextSequence) {
    }

    public record ResultResponse(String protocol, String version, String executionId,
            String correlationId, String status, String summary, String patch,
            List<String> modifiedFiles, List<Map<String, Object>> artifacts,
            Map<String, Object> metrics, String completedAt) {
    }

    public record CancelResponse(String protocol, String version, String executionId,
            String status, String message) {
    }

    public record HealthResponse(String protocol, String version, String status,
            String application, String appVersion, String engineVersion, String nodeId,
            long uptimeSeconds, boolean ready, String timestamp, Map<String, Object> details) {
    }

    public record ModelDescriptor(String id, String name, String provider, boolean available,
            int contextWindowTokens, boolean recommended) {
    }

    public record CapabilityDescriptor(String id, String name, List<String> supportedModes,
            List<String> requiredScopes) {
    }

    /**
     * Agent descriptor. The unified multi-agent dimension extends the original
     * (name, description) shape additively: preferredHarness/preferredProvider/preferredModel
     * and toolPermissions describe reusable agent profiles; harness-native agents keep the
     * new fields empty. Existing consumers only reading name/description remain compatible.
     */
    public record AgentDescriptor(String name, String description, String preferredHarness,
            String preferredProvider, String preferredModel, List<String> toolPermissions) {
    }

    public record ToolDescriptor(String name, String description, boolean readOnly) {
    }

    public record CapabilitiesResponse(String protocol, String version, String timestamp,
            List<CapabilityDescriptor> capabilities, List<String> supportedModes,
            List<AgentDescriptor> agents, List<ModelDescriptor> availableModels,
            List<ToolDescriptor> tools, Map<String, Boolean> features) {
    }

    public record ErrorEnvelope(String protocol, String version, String code, String message,
            String requestId, String correlationId, boolean retryable, String timestamp,
            Map<String, Object> details) {
    }

    public enum ErrorCode {
        AUTHENTICATION_FAILED(401, false),
        AUTHORIZATION_DENIED(403, false),
        VALIDATION_FAILED(400, false),
        NOT_FOUND(404, false),
        CONFLICT(409, false),
        CAPABILITY_UNSUPPORTED(422, false),
        INTERNAL_ERROR(500, false),
        SERVICE_UNAVAILABLE(503, true),
        TIMEOUT(504, true),
        VERSION_MISMATCH(400, false),
        METHOD_NOT_ALLOWED(405, false);

        private final int httpStatus;
        private final boolean retryable;

        ErrorCode(int httpStatus, boolean retryable) {
            this.httpStatus = httpStatus;
            this.retryable = retryable;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public boolean retryable() {
            return retryable;
        }
    }
}
