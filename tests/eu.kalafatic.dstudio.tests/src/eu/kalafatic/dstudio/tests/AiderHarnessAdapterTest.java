package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionSink;
import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import eu.kalafatic.dstudio.harness.aider.AiderHarnessAdapter;
import eu.kalafatic.dstudio.infrastructure.git.JGitWorkspaceDiffService;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiderHarnessAdapterTest {

    @TempDir
    Path tempDir;

    private static class RecordingSink implements HarnessExecutionSink {
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
    }

    private AiderHarnessAdapter newAdapter() {
        return new AiderHarnessAdapter(new JGitWorkspaceDiffService());
    }

    @Test
    void identityAndCapabilities() {
        AiderHarnessAdapter adapter = newAdapter();
        assertEquals("aider", adapter.getHarnessId());
        assertEquals("Aider CLI", adapter.getHarnessName());
        assertEquals("AIDER", adapter.getHarnessType());
        assertTrue(adapter.getCapabilities().contains("CODE_IMPLEMENTATION"));
    }

    @Test
    void connectionTestWithInvalidBinaryFails() {
        AiderHarnessAdapter adapter = newAdapter();
        adapter.updateConfiguration(Map.of("binary", "/nonexistent/aider-binary"));
        assertFalse(adapter.testConnection().success());
    }

    @Test
    void connectionTestAgainstInstalledBinary() {
        AiderHarnessAdapter adapter = newAdapter();
        var result = adapter.testConnection();
        if (result.success()) {
            assertTrue(result.message().contains("Aider CLI available"));
        }
    }

    @Test
    void configurationUpdateRoundTrip() {
        AiderHarnessAdapter adapter = newAdapter();
        adapter.updateConfiguration(Map.of("binary", "/custom/aider",
                "model", "ollama/qwen3-coder-30b-32k:latest",
                "timeoutSeconds", "600"));
        Map<String, String> config = adapter.getConnectionConfiguration();
        assertEquals("/custom/aider", config.get("binary"));
        assertEquals("ollama/qwen3-coder-30b-32k:latest", config.get("model"));
    }

    @Test
    void missingWorkspaceRejected() {
        AiderHarnessAdapter adapter = newAdapter();
        HarnessExecutionResult result = adapter.execute(new HarnessExecutionRequest(
                "task-1", tempDir.resolve("missing-dir"), "instruction", null, null,
                null, false, Duration.ofMinutes(1), () -> false), new RecordingSink());
        assertTrue(result.handled());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("does not exist"));
    }

    @Test
    void missingModelRejectedHonestly() throws Exception {
        AiderHarnessAdapter adapter = newAdapter();
        Path dir = tempDir.resolve("ws");
        java.nio.file.Files.createDirectories(dir);
        HarnessExecutionResult result = adapter.execute(new HarnessExecutionRequest(
                "task-1", dir, "instruction", null, null, null, false,
                Duration.ofMinutes(1), () -> false), new RecordingSink());
        assertTrue(result.handled());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("requires a model"), result.errorMessage());
    }

    @Test
    void modelNormalizationAndArtifactFilter() {
        assertEquals("ollama/qwen3-coder-30b-32k:latest",
                AiderHarnessAdapter.normalizeModel("qwen3-coder-30b-32k:latest"));
        assertEquals("deepseek/deepseek-chat",
                AiderHarnessAdapter.normalizeModel("deepseek/deepseek-chat"));
        assertEquals("", AiderHarnessAdapter.normalizeModel(""));
        assertTrue(AiderHarnessAdapter.isAiderArtifact(".aider.chat.history.md"));
        assertTrue(AiderHarnessAdapter.isAiderArtifact(".aider.input.history"));
        assertFalse(AiderHarnessAdapter.isAiderArtifact("src/Main.java"));
    }

    @Test
    void ansiCodesStripped() {
        assertEquals("Hello", AiderHarnessAdapter.stripAnsi("\u001B[1mHello\u001B[0m"));
        assertEquals("plain", AiderHarnessAdapter.stripAnsi("plain"));
        assertEquals("", AiderHarnessAdapter.stripAnsi(null));
    }
}
