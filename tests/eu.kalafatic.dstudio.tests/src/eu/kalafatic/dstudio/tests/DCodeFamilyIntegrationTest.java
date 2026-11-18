package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionResult;
import eu.kalafatic.dstudio.harness.dcode.DCodeHarnessAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Family integration (Phase 8, §10): DStudio orchestrates DCode as its native EVO
 * Family coding harness through the FHC 1.0 contract. Requires a running DCode
 * headless server (default http://localhost:48080, override with
 * -Ddcode.live.endpoint=...). Skips silently when DCode is absent.
 *
 * <p>Verified live: DStudio creates a workspace/repository/session in DCode, DCode's
 * native engine produces REAL file changes, and DStudio collects the changed files and
 * diff for review.</p>
 */
class DCodeFamilyIntegrationTest {

    private static final String DEFAULT_ENDPOINT = "http://localhost:48080";

    @TempDir
    Path tempDir;

    private String endpoint() {
        return System.getProperty("dcode.live.endpoint", DEFAULT_ENDPOINT);
    }

    @Test
    @Timeout(300)
    void dstudioExecutesTaskThroughLiveDCode() throws Exception {
        DCodeHarnessAdapter adapter = new DCodeHarnessAdapter();
        adapter.updateConfiguration(Map.of("endpoint", endpoint()));
        if (!adapter.testConnection().success()) {
            return; // DCode server not running: silent skip per family convention
        }
        Path workspace = tempDir.resolve("family-ws");
        Files.createDirectories(workspace);
        List<String> logLines = new ArrayList<>();
        HarnessExecutionResult result = adapter.execute(new HarnessExecutionRequest(
                "family-dcode-1", workspace,
                "Create a file named family-marker.txt containing exactly: FAMILY_OK",
                null, null, null, false, Duration.ofMinutes(5), () -> false),
                new eu.kalafatic.dstudio.core.harness.HarnessExecutionSink() {
                    @Override
                    public void emit(eu.kalafatic.dstudio.core.model.ExecutionEventType type,
                            String message) {
                        logLines.add(type.name() + ": " + message);
                    }

                    @Override
                    public void emit(eu.kalafatic.dstudio.core.model.ExecutionEventType type,
                            String message, Map<String, Object> data) {
                        logLines.add(type.name() + ": " + message);
                    }

                    @Override
                    public void progress(double progress, String message) {
                    }

                    @Override
                    public void log(String line) {
                        logLines.add(line);
                    }
                });
        assertTrue(result.success(), "DCode execution must succeed: "
                + result.errorMessage());
        assertTrue(Files.isRegularFile(workspace.resolve("family-marker.txt")),
                "DCode must create the requested file on disk");
        assertEquals("FAMILY_OK",
                Files.readString(workspace.resolve("family-marker.txt")).trim());
        assertFalse(result.modifiedFiles().isEmpty(),
                "DStudio must collect the changed files from DCode");
        assertTrue(result.modifiedFiles().contains("family-marker.txt"));
        assertTrue(result.diffText().contains("family-marker.txt"),
                "DStudio must collect the diff from DCode");
        System.out.println("[DCodeFamilyIntegrationTest] live family execution: files="
                + result.modifiedFiles() + " logLines=" + logLines.size());
    }

    @Test
    @Timeout(60)
    void dcodeAvailabilityIsReportedHonestly() {
        DCodeHarnessAdapter adapter = new DCodeHarnessAdapter();
        adapter.updateConfiguration(Map.of("endpoint", endpoint()));
        var result = adapter.testConnection();
        if (result.success()) {
            assertTrue(result.message().contains("available"), result.message());
        } else {
            // absent DCode must produce a clear, honest diagnostic
            assertTrue(result.message().contains("DCode"), result.message());
        }
    }
}
