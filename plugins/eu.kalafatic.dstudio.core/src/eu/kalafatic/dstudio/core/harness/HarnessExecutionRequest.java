package eu.kalafatic.dstudio.core.harness;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Execution request handed to a harness adapter. Carries the fully resolved execution
 * environment (harness, provider, model, agent profile instructions/permissions) so the
 * adapter never guesses or silently overrides the selected configuration.
 */
public record HarnessExecutionRequest(String taskId, Path workingDirectory, String instruction,
        String prompt, String model, String agent, boolean autoApprove, Duration timeout,
        BooleanSupplier cancellationSupplier, String providerId, String agentProfileId,
        String systemInstructions, List<String> toolPermissions,
        Map<String, String> executionParameters) {

    public HarnessExecutionRequest {
        timeout = timeout == null ? Duration.ofMinutes(30) : timeout;
        cancellationSupplier = cancellationSupplier == null ? () -> false : cancellationSupplier;
        providerId = providerId == null ? "" : providerId;
        agentProfileId = agentProfileId == null ? "" : agentProfileId;
        systemInstructions = systemInstructions == null ? "" : systemInstructions;
        toolPermissions = List.copyOf(toolPermissions == null ? List.of() : toolPermissions);
        executionParameters = Map.copyOf(executionParameters == null ? Map.of()
                : executionParameters);
    }

    /** Legacy-compatible constructor (execution environment fields unset). */
    public HarnessExecutionRequest(String taskId, Path workingDirectory, String instruction,
            String prompt, String model, String agent, boolean autoApprove, Duration timeout,
            BooleanSupplier cancellationSupplier) {
        this(taskId, workingDirectory, instruction, prompt, model, agent, autoApprove, timeout,
                cancellationSupplier, "", "", "", List.of(), Map.of());
    }
}
