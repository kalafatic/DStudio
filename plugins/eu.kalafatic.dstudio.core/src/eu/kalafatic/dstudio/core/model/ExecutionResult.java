package eu.kalafatic.dstudio.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExecutionResult(String executionId, String harnessId, ExecutionStatus status,
        String summary, String errorMessage, int exitCode, boolean timedOut, boolean cancelled,
        List<String> modifiedFiles, String diffText, Instant startedAt, Instant completedAt,
        Map<String, Long> metrics) {

    public static ExecutionResult empty(String executionId) {
        return new ExecutionResult(executionId, "", ExecutionStatus.QUEUED, "", "", -1, false,
                false, List.of(), "", null, null, Map.of());
    }
}
