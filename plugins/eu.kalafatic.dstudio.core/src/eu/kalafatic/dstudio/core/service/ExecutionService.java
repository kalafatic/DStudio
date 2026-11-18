package eu.kalafatic.dstudio.core.service;

import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionResult;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExecutionService {

    record ExecutionRequest(String harnessId, String projectId, String idempotencyKey,
            String instruction, String prompt, String model, String agent, boolean autoApprove,
            String workingDirectory, Duration timeout, String providerId, String agentProfileId,
            Map<String, String> executionParameters, List<String> toolPermissions) {

        public ExecutionRequest {
            timeout = timeout == null ? Duration.ofMinutes(30) : timeout;
            executionParameters = executionParameters == null ? Map.of()
                    : Map.copyOf(executionParameters);
            toolPermissions = List.copyOf(toolPermissions == null ? List.of() : toolPermissions);
        }

        /** Legacy-compatible constructor (provider/agent-profile/parameters unset). */
        public ExecutionRequest(String harnessId, String projectId, String idempotencyKey,
                String instruction, String prompt, String model, String agent,
                boolean autoApprove, String workingDirectory, Duration timeout) {
            this(harnessId, projectId, idempotencyKey, instruction, prompt, model, agent,
                    autoApprove, workingDirectory, timeout, "", "", Map.of(), List.of());
        }
    }

    ExecutionTask submit(ExecutionRequest request);

    void cancel(String executionId);

    Optional<ExecutionTask> getTask(String executionId);

    Optional<ExecutionTask> findByIdempotencyKey(String idempotencyKey);

    Optional<ExecutionResult> getResult(String executionId);

    List<ExecutionEvent> getEvents(String executionId, long afterSequence, int limit);

    List<ExecutionTask> listTasks();

    List<ExecutionTask> listActiveTasks();

    void reconcileAfterRestart();

    void shutdown();
}
