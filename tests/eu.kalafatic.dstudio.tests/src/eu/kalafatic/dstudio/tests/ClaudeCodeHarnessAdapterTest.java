package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionSink;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.harness.claude.ClaudeCodeHarnessAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 7 (S2): Claude Code adapter contract tests — config, capability declaration
 * (Anthropic-native), honest failure reporting. The execution recipe is covered by
 * {@link #liveExecutionSkipsWithoutCli()} when a real CLI is present; on hosts without
 * the {@code claude} binary the live test skips silently (execution NOT live-verified).
 */
class ClaudeCodeHarnessAdapterTest {

    @TempDir
    Path tempDir;

    private ClaudeCodeHarnessAdapter adapter() {
        return new ClaudeCodeHarnessAdapter(null);
    }

    private static final HarnessExecutionSink NOOP_SINK = new HarnessExecutionSink() {
        @Override
        public void emit(ExecutionEventType type, String message) {
        }

        @Override
        public void emit(ExecutionEventType type, String message,
                Map<String, Object> data) {
        }

        @Override
        public void progress(double progress, String message) {
        }

        @Override
        public void log(String line) {
        }
    };

    @Test
    void identityAndCapabilities() {
        ClaudeCodeHarnessAdapter adapter = adapter();
        assertEquals("claude", adapter.getHarnessId());
        assertEquals("CLAUDE", adapter.getHarnessType());
        HarnessCapabilityDescriptor descriptor = adapter.getCapabilityDescriptor();
        assertTrue(descriptor.capabilities().contains("CODE_IMPLEMENTATION"));
        assertTrue(descriptor.capabilities().contains("CODE_ANALYSIS"));
        assertEquals(java.util.List.of("ANTHROPIC"),
                descriptor.supportedProviderTypes());
        assertTrue(descriptor.features().getOrDefault("cancellation", false));
    }

    @Test
    void configDefaultsAndUnknownBinaryReportsHonestly() {
        ClaudeCodeHarnessAdapter adapter = adapter();
        adapter.updateConfiguration(Map.of("binary", "/nonexistent/claude-binary"));
        var result = adapter.testConnection();
        assertFalse(result.success());
        assertTrue(result.message().contains("not found"), result.message());
        assertEquals("900", adapter.getConnectionConfiguration().get("timeoutSeconds"));
    }

    @Test
    void missingWorkspaceRejectedWithoutCli() {
        HarnessExecutionResult result = adapter().execute(new HarnessExecutionRequest(
                "task-1", tempDir.resolve("missing-ws"), "instruction", null, null, null,
                false, Duration.ofMinutes(1), () -> false), NOOP_SINK);
        assertTrue(result.handled());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("does not exist"), result.errorMessage());
    }

    @Test
    @Timeout(120)
    void liveExecutionSkipsWithoutCli() throws Exception {
        ClaudeCodeHarnessAdapter probe = new ClaudeCodeHarnessAdapter(null);
        if (!probe.testConnection().success()) {
            return; // CLI absent on this host: execution remains live-unverified
        }
        Path workspace = tempDir.resolve("claude-live-ws");
        Files.createDirectories(workspace);
        // real-execution gate: a real CLI run must create the requested file
        HarnessExecutionResult result = probe.execute(new HarnessExecutionRequest(
                "task-claude-live", workspace,
                "Create a file named claude-live-marker.txt containing exactly: LIVES",
                null, null, null, false, Duration.ofMinutes(5), () -> false), NOOP_SINK);
        assertTrue(result.success(), "live execution must succeed: " + result.errorMessage());
        Path marker = workspace.resolve("claude-live-marker.txt");
        assertTrue(Files.isRegularFile(marker), "marker file must exist");
        assertEquals("LIVES", Files.readString(marker).trim());
    }
}
