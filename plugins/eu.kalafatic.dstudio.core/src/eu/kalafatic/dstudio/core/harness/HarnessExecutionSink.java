package eu.kalafatic.dstudio.core.harness;

import eu.kalafatic.dstudio.core.model.ExecutionEventType;
import java.util.Map;

public interface HarnessExecutionSink {

    void emit(ExecutionEventType type, String message);

    default void emit(ExecutionEventType type, String message, Map<String, Object> data) {
        emit(type, message);
    }

    void progress(double progress, String message);

    void log(String line);
}
