package eu.kalafatic.dstudio.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unified execution configuration (Phase 9): harness, provider (inference engine), model
 * and agent profile plus execution parameters, tool permissions, resource limits and
 * adapter-specific settings. Supports scope-based inheritance (GLOBAL → PROJECT → TASK →
 * EXECUTION); more specific non-blank values override inherited ones. Configurations never
 * contain secrets — credentials live in the encrypted adapter stores and are resolved by
 * the adapters at execution time.
 */
public record ExecutionConfiguration(
        String harnessId,
        String providerId,
        String modelId,
        String agentProfileId,
        Map<String, String> executionParameters,
        List<String> toolPermissions,
        Map<String, String> resourceLimits,
        Map<String, String> harnessSettings,
        String source
) {

    public static final String SOURCE_GLOBAL = "GLOBAL";
    public static final String SOURCE_PROJECT = "PROJECT";
    public static final String SOURCE_TASK = "TASK";
    public static final String SOURCE_EXECUTION = "EXECUTION";

    public ExecutionConfiguration {
        executionParameters = immutableMap(executionParameters);
        toolPermissions = List.copyOf(toolPermissions == null ? List.of() : toolPermissions);
        resourceLimits = immutableMap(resourceLimits);
        harnessSettings = immutableMap(harnessSettings);
        source = (source == null || source.isBlank()) ? SOURCE_TASK : source;
    }

    private static Map<String, String> immutableMap(Map<String, String> source) {
        return Map.copyOf(new LinkedHashMap<>(source == null ? Map.of() : source));
    }

    /** Returns an empty configuration for the given source (pure defaults container). */
    public static ExecutionConfiguration empty(String source) {
        return new ExecutionConfiguration("", "", "", "", Map.of(), List.of(), Map.of(),
                Map.of(), source);
    }

    /**
     * Resolution step: every non-blank scalar, non-empty collection and non-empty map from
     * {@code moreSpecific} overrides the value inherited from {@code this}.
     */
    public ExecutionConfiguration overrideWith(ExecutionConfiguration moreSpecific) {
        if (moreSpecific == null) {
            return this;
        }
        Map<String, String> params = new LinkedHashMap<>(executionParameters);
        params.putAll(moreSpecific.executionParameters());
        Map<String, String> limits = new LinkedHashMap<>(resourceLimits);
        limits.putAll(moreSpecific.resourceLimits());
        Map<String, String> settings = new LinkedHashMap<>(harnessSettings);
        settings.putAll(moreSpecific.harnessSettings());
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>(toolPermissions);
        if (!moreSpecific.toolPermissions().isEmpty()) {
            permissions = new java.util.ArrayList<>(moreSpecific.toolPermissions());
        }
        return new ExecutionConfiguration(
                pick(harnessId, moreSpecific.harnessId()),
                pick(providerId, moreSpecific.providerId()),
                pick(modelId, moreSpecific.modelId()),
                pick(agentProfileId, moreSpecific.agentProfileId()),
                params,
                permissions,
                limits,
                settings,
                moreSpecific.source());
    }

    private static String pick(String inherited, String moreSpecific) {
        return (moreSpecific != null && !moreSpecific.isBlank()) ? moreSpecific : inherited;
    }

    public boolean isFullySpecified() {
        return !isBlank(harnessId) && !isBlank(providerId) && !isBlank(modelId)
                && !isBlank(agentProfileId);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "ExecutionConfiguration[source=" + source + ", harness=" + harnessId
                + ", provider=" + providerId + ", model=" + modelId
                + ", agent=" + agentProfileId + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ExecutionConfiguration that)) {
            return false;
        }
        return Objects.equals(harnessId, that.harnessId)
                && Objects.equals(providerId, that.providerId)
                && Objects.equals(modelId, that.modelId)
                && Objects.equals(agentProfileId, that.agentProfileId)
                && executionParameters.equals(that.executionParameters)
                && toolPermissions.equals(that.toolPermissions)
                && resourceLimits.equals(that.resourceLimits)
                && harnessSettings.equals(that.harnessSettings)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(harnessId, providerId, modelId, agentProfileId,
                executionParameters, toolPermissions, resourceLimits, harnessSettings, source);
    }
}
