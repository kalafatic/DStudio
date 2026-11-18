package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionSink;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.harness.opencode.OpenCodeHarnessAdapter;
import eu.kalafatic.dstudio.infrastructure.config.DStudioPaths;
import eu.kalafatic.dstudio.infrastructure.git.JGitWorkspaceDiffService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenCodeHarnessAdapterTest {

    @TempDir
    Path tempDir;

    private static class RecordingSink implements HarnessExecutionSink {
        final List<String> lines = new ArrayList<>();
        final List<ExecutionEventType> types = new ArrayList<>();

        @Override
        public void emit(ExecutionEventType type, String message) {
            types.add(type);
            lines.add(message);
        }

        @Override
        public void emit(ExecutionEventType type, String message,
                Map<String, Object> data) {
            types.add(type);
            lines.add(message);
        }

        @Override
        public void progress(double progress, String message) {
        }

        @Override
        public void log(String line) {
            lines.add(line);
        }
    }

    private OpenCodeHarnessAdapter newAdapter() {
        return new OpenCodeHarnessAdapter(new JGitWorkspaceDiffService(),
                new DStudioPaths(tempDir.resolve("dstudio-home")));
    }

    @Test
    void identityAndCapabilities() {
        OpenCodeHarnessAdapter adapter = newAdapter();
        assertEquals("opencode", adapter.getHarnessId());
        assertEquals("OpenCode CLI", adapter.getHarnessName());
        assertEquals("OPENCODE", adapter.getHarnessType());
        assertTrue(adapter.getCapabilities().contains("CODE_IMPLEMENTATION"));
        assertTrue(adapter.getCapabilityDescriptor().features().get("cancellation"));
    }

    @Test
    void connectionTestWithInvalidBinaryFails() {
        OpenCodeHarnessAdapter adapter = newAdapter();
        adapter.updateConfiguration(Map.of("binary", "/nonexistent/opencode-binary"));
        assertFalse(adapter.testConnection().success());
    }

    @Test
    void connectionTestAgainstInstalledBinary() {
        OpenCodeHarnessAdapter adapter = newAdapter();
        var result = adapter.testConnection();
        if (result.success()) {
            assertTrue(result.message().contains("OpenCode CLI available"));
        }
    }

    @Test
    void configurationUpdateRoundTrip() {
        OpenCodeHarnessAdapter adapter = newAdapter();
        adapter.updateConfiguration(Map.of("binary", "/custom/opencode",
                "agent", "build", "model", "ollama/test:latest",
                "autoApprove", "true", "timeoutSeconds", "600"));
        Map<String, String> config = adapter.getConnectionConfiguration();
        assertEquals("/custom/opencode", config.get("binary"));
        assertEquals("ollama/test:latest", config.get("model"));
        assertEquals("true", config.get("autoApprove"));
    }

    @Test
    void normalizeModelPrefixesBareIds() {
        assertEquals("ollama/qwen3-coder-30b-32k:latest",
                OpenCodeHarnessAdapter.normalizeModel("qwen3-coder-30b-32k:latest"));
        assertEquals("ollama/test:latest", OpenCodeHarnessAdapter.normalizeModel("test:latest"));
        assertEquals("deepseek/deepseek-v4-pro",
                OpenCodeHarnessAdapter.normalizeModel("deepseek/deepseek-v4-pro"));
        assertEquals("", OpenCodeHarnessAdapter.normalizeModel(""));
        assertEquals("", OpenCodeHarnessAdapter.normalizeModel(null));
    }

    @Test
    void containmentConstraintsAppendedToPrompt() {
        String constrained = OpenCodeHarnessAdapter.applyContainmentConstraints("Do X",
                Path.of("/tmp/ws"));
        assertTrue(constrained.startsWith("Do X"));
        assertTrue(constrained.contains("[EXECUTION CONSTRAINTS]"));
        assertTrue(constrained.contains("/tmp/ws"));
    }

    @Test
    void missingWorkspaceRejected() {
        OpenCodeHarnessAdapter adapter = newAdapter();
        HarnessExecutionResult result = adapter.execute(new HarnessExecutionRequest(
                "task-1", tempDir.resolve("missing-dir"), "instruction", null, null,
                null, false, Duration.ofMinutes(1), () -> false), new RecordingSink());
        assertTrue(result.handled());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("does not exist"));
    }
}
