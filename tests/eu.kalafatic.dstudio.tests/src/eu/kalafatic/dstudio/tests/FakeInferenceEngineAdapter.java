package eu.kalafatic.dstudio.tests;

import eu.kalafatic.dstudio.core.inference.InferenceEngineAdapter;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Fake inference engine for registry and HTTP tests (Phase 6).
 */
public class FakeInferenceEngineAdapter implements InferenceEngineAdapter {

    private final String id;
    private final String type;
    private boolean available = true;
    private String answer = "fake answer";
    private volatile boolean fail;
    private volatile InferenceChatRequest lastRequest;

    public FakeInferenceEngineAdapter(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setFail(boolean fail) {
        this.fail = fail;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public InferenceChatRequest getLastRequest() {
        return lastRequest;
    }

    @Override
    public String getEngineId() {
        return id;
    }

    @Override
    public String getEngineName() {
        return "Fake " + id;
    }

    @Override
    public String getEngineType() {
        return type;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public InferenceConnectionTestResult testConnection() {
        return available ? InferenceConnectionTestResult.ok(id + " available")
                : InferenceConnectionTestResult.error(id + " unavailable");
    }

    @Override
    public List<InferenceModel> listModels() {
        return List.of(new InferenceModel(id + ":demo:1", "demo:1", id, "fake", 1024, true));
    }

    @Override
    public InferenceRuntimeStatus runtimeStatus() {
        return new InferenceRuntimeStatus(true, 4, 0.5, 1024, 128, false, "", 0, 0, 0,
                List.of("fake"));
    }

    @Override
    public boolean loadModel(String modelId) {
        return true;
    }

    @Override
    public boolean unloadModel(String modelId) {
        return true;
    }

    @Override
    public InferenceChatResponse chat(InferenceChatRequest request) {
        lastRequest = request;
        if (fail) {
            throw new IllegalStateException("fake engine failure");
        }
        return new InferenceChatResponse(answer, "stop", "fake", request.model(), 5);
    }

    @Override
    public void streamChat(InferenceChatRequest request, Consumer<InferenceChatChunk> chunkConsumer,
            BooleanSupplier cancellationSupplier) {
        lastRequest = request;
        if (fail) {
            throw new IllegalStateException("fake engine failure");
        }
        chunkConsumer.accept(new InferenceChatChunk(answer, false, "fake"));
        chunkConsumer.accept(InferenceChatChunk.done("fake"));
    }

    @Override
    public void updateConfiguration(Map<String, String> configuration) {
        // fake keeps config in memory only
    }
}
