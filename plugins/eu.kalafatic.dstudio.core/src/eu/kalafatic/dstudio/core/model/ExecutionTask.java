package eu.kalafatic.dstudio.core.model;

import java.time.Instant;
import java.util.Map;

public class ExecutionTask {

    private final String id;
    private final String harnessId;
    private final String projectId;
    private final String idempotencyKey;
    private final String instruction;
    private final String prompt;
    private final String model;
    private final String agent;
    private final boolean autoApprove;
    private final String workingDirectory;
    private final String providerId;
    private final String agentProfileId;
    private final java.util.Map<String, String> executionParameters;
    private final Instant createdAt;
    private volatile ExecutionStatus status;
    private volatile Instant startedAt;
    private volatile Instant completedAt;
    private volatile double progress;
    private volatile String statusMessage;
    private volatile int exitCode;
    private volatile String summary;
    private volatile String errorMessage;
    private volatile boolean timedOut;

    public ExecutionTask(String id, String harnessId, String projectId, String idempotencyKey,
            String instruction, String prompt, String model, String agent, boolean autoApprove,
            String workingDirectory, Instant createdAt) {
        this(id, harnessId, projectId, idempotencyKey, instruction, prompt, model, agent,
                autoApprove, workingDirectory, "", "", Map.of(), createdAt);
    }

    public ExecutionTask(String id, String harnessId, String projectId, String idempotencyKey,
            String instruction, String prompt, String model, String agent, boolean autoApprove,
            String workingDirectory, String providerId, String agentProfileId,
            java.util.Map<String, String> executionParameters, Instant createdAt) {
        this.id = id;
        this.harnessId = harnessId;
        this.projectId = projectId;
        this.idempotencyKey = idempotencyKey;
        this.instruction = instruction;
        this.prompt = prompt;
        this.model = model;
        this.agent = agent;
        this.autoApprove = autoApprove;
        this.workingDirectory = workingDirectory;
        this.providerId = providerId == null ? "" : providerId;
        this.agentProfileId = agentProfileId == null ? "" : agentProfileId;
        this.executionParameters = java.util.Map.copyOf(executionParameters == null
                ? Map.of() : executionParameters);
        this.createdAt = createdAt;
        this.status = ExecutionStatus.QUEUED;
        this.progress = 0.0;
        this.statusMessage = "";
    }

    public String getId() { return id; }
    public String getHarnessId() { return harnessId; }
    public String getProjectId() { return projectId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getInstruction() { return instruction; }
    public String getPrompt() { return prompt; }
    public String getModel() { return model; }
    public String getAgent() { return agent; }
    public boolean isAutoApprove() { return autoApprove; }
    public String getWorkingDirectory() { return workingDirectory; }
    public String getProviderId() { return providerId; }
    public String getAgentProfileId() { return agentProfileId; }
    public java.util.Map<String, String> getExecutionParameters() { return executionParameters; }
    public Instant getCreatedAt() { return createdAt; }
    public ExecutionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public double getProgress() { return progress; }
    public String getStatusMessage() { return statusMessage; }
    public int getExitCode() { return exitCode; }
    public String getSummary() { return summary; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isTimedOut() { return timedOut; }

    public void markRunning() {
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = Instant.now();
        this.statusMessage = "Running";
    }

    public void updateProgress(double newProgress, String message) {
        this.progress = Math.max(0.0, Math.min(1.0, newProgress));
        this.statusMessage = message == null ? this.statusMessage : message;
    }

    public void markCompleted(String newSummary, int newExitCode) {
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.summary = newSummary;
        this.exitCode = newExitCode;
        this.progress = 1.0;
    }

    public void markFailed(String newErrorMessage, int newExitCode) {
        this.status = ExecutionStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = newErrorMessage;
        this.exitCode = newExitCode;
    }

    public void markCancelled(String reason) {
        this.status = ExecutionStatus.CANCELLED;
        this.completedAt = Instant.now();
        this.statusMessage = reason == null ? "Cancelled" : reason;
    }

    public void markTimedOut(String reason) {
        this.status = ExecutionStatus.TIMED_OUT;
        this.completedAt = Instant.now();
        this.timedOut = true;
        this.statusMessage = reason == null ? "Timed out" : reason;
    }

    public void markNotResumed(String reason) {
        this.status = ExecutionStatus.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = reason;
    }

    public boolean isTerminal() {
        return status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED
                || status == ExecutionStatus.CANCELLED || status == ExecutionStatus.TIMED_OUT;
    }
}
