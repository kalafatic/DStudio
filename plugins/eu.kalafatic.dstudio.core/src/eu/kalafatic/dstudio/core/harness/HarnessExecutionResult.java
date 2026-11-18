package eu.kalafatic.dstudio.core.harness;

import java.util.List;

public record HarnessExecutionResult(boolean handled, boolean success, boolean timedOut,
        boolean cancelled, int exitCode, String summary, String errorMessage,
        List<String> modifiedFiles, String diffText) {

    public static HarnessExecutionResult notHandled() {
        return new HarnessExecutionResult(false, false, false, false, -1, "", "", List.of(), "");
    }

    public static HarnessExecutionResult ok(String summary, List<String> modifiedFiles) {
        return new HarnessExecutionResult(true, true, false, false, 0,
                summary == null ? "" : summary, "", modifiedFiles == null ? List.of()
                        : List.copyOf(modifiedFiles), "");
    }

    public static HarnessExecutionResult ok(String summary, List<String> modifiedFiles,
            String diffText) {
        return new HarnessExecutionResult(true, true, false, false, 0,
                summary == null ? "" : summary, "", modifiedFiles == null ? List.of()
                        : List.copyOf(modifiedFiles),
                diffText == null ? "" : diffText);
    }

    public static HarnessExecutionResult error(int exitCode, String error) {
        return new HarnessExecutionResult(true, false, false, false, exitCode, "",
                error == null ? "" : error, List.of(), "");
    }

    public static HarnessExecutionResult timedOut(String error) {
        return new HarnessExecutionResult(true, false, true, false, -1, "",
                error == null ? "Timed out" : error, List.of(), "");
    }

    public static HarnessExecutionResult cancelled(String reason) {
        return new HarnessExecutionResult(true, false, false, true, -1, "",
                reason == null ? "Cancelled" : reason, List.of(), "");
    }
}
