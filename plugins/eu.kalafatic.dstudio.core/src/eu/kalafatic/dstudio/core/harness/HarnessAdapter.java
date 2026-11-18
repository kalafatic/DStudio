package eu.kalafatic.dstudio.core.harness;

import java.util.List;
import java.util.Map;

public interface HarnessAdapter {

    String getHarnessId();

    String getHarnessName();

    String getHarnessType();

    List<String> getCapabilities();

    HarnessCapabilityDescriptor getCapabilityDescriptor();

    boolean isAvailable();

    HarnessConnectionTestResult testConnection();

    default void updateConfiguration(Map<String, String> configuration) {
    }

    default Map<String, String> getConnectionConfiguration() {
        return Map.of();
    }

    HarnessExecutionResult execute(HarnessExecutionRequest request, HarnessExecutionSink sink);
}
