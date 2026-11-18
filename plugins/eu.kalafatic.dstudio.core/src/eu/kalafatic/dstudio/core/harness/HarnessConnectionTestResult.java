package eu.kalafatic.dstudio.core.harness;

public record HarnessConnectionTestResult(boolean success, String message) {

    public static HarnessConnectionTestResult ok(String message) {
        return new HarnessConnectionTestResult(true, message == null ? "" : message);
    }

    public static HarnessConnectionTestResult error(String message) {
        return new HarnessConnectionTestResult(false, message == null ? "Unavailable" : message);
    }
}
