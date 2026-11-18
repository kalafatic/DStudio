package eu.kalafatic.dstudio.tests;

import eu.kalafatic.dstudio.core.harness.HarnessAdapter;
import eu.kalafatic.dstudio.core.harness.HarnessCapabilityDescriptor;
import eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionRequest;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionResult;
import eu.kalafatic.dstudio.core.harness.HarnessExecutionSink;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FakeHarnessAdapter implements HarnessAdapter {

    public enum Mode { COMPLETE, FAIL, HANG, SLOW }

    private final String id;
    private final String type;
    private final Map<String, String> config = new LinkedHashMap<>();
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.COMPLETE);
    private final AtomicReference<List<String>> modifiedFiles = new AtomicReference<>(
            List.of("src/Changed.java"));
    private final AtomicReference<String> errorMessage = new AtomicReference<>(
            "Fake harness failed");
    private final CountDownLatch started = new CountDownLatch(1);
    private volatile HarnessConnectionTestResult connectionResult =
            HarnessConnectionTestResult.ok("fake available");

    public FakeHarnessAdapter(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public void setMode(Mode newMode) {
        mode.set(newMode);
    }

    public void setConnectionResult(HarnessConnectionTestResult result) {
        this.connectionResult = result;
    }

    public boolean awaitStart(long timeoutMillis) throws InterruptedException {
        return started.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getHarnessId() {
        return id;
    }

    @Override
    public String getHarnessName() {
        return "Fake Harness " + id;
    }

    @Override
    public String getHarnessType() {
        return type;
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("CODE_IMPLEMENTATION");
    }

    @Override
    public HarnessCapabilityDescriptor getCapabilityDescriptor() {
        return new HarnessCapabilityDescriptor(id, getHarnessName(), type,
                List.of("CODE_IMPLEMENTATION"), List.of("DEFAULT"), List.of(),
                List.of(), Map.of("asyncExecution", true, "eventStreaming", true,
                        "cancellation", true), List.of("OLLAMA", "OPENAI_COMPATIBLE"));
    }

    @Override
    public boolean isAvailable() {
        return connectionResult.success();
    }

    @Override
    public HarnessConnectionTestResult testConnection() {
        return connectionResult;
    }

    @Override
    public void updateConfiguration(Map<String, String> configuration) {
        config.clear();
        config.putAll(configuration);
    }

    @Override
    public Map<String, String> getConnectionConfiguration() {
        return Map.copyOf(config);
    }

    @Override
    public HarnessExecutionResult execute(HarnessExecutionRequest request,
            HarnessExecutionSink sink) {
        started.countDown();
        sink.emit(eu.kalafatic.dstudio.core.model.ExecutionEventType.EXECUTION_LOG,
                "fake harness started");
        switch (mode.get()) {
            case FAIL -> {
                return HarnessExecutionResult.error(3, errorMessage.get());
            }
            case HANG -> {
                long deadline = System.currentTimeMillis() + 60000;
                while (System.currentTimeMillis() < deadline) {
                    if (request.cancellationSupplier().getAsBoolean()) {
                        return HarnessExecutionResult.cancelled("Cancelled by test");
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return HarnessExecutionResult.cancelled("Interrupted");
                    }
                }
                return HarnessExecutionResult.error(-1, "Hang timeout in fake harness");
            }
            case SLOW -> {
                long deadline = System.currentTimeMillis()
                        + request.timeout().toMillis();
                while (System.currentTimeMillis() < deadline) {
                    if (request.cancellationSupplier().getAsBoolean()) {
                        return HarnessExecutionResult.cancelled("Cancelled by test");
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return HarnessExecutionResult.timedOut("fake harness timeout");
            }
            default -> {
                sink.log("fake harness completed");
                return HarnessExecutionResult.ok("fake summary", modifiedFiles.get());
            }
        }
    }
}
