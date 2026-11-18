package eu.kalafatic.dstudio.core.harness;

import java.util.List;
import java.util.Map;

public record HarnessCapabilityDescriptor(String id, String name, String type,
        List<String> capabilities, List<String> supportedModes, List<ModelDescriptor> models,
        List<String> agents, Map<String, Boolean> features,
        List<String> supportedProviderTypes) {

    public record ModelDescriptor(String id, String name, String provider, boolean available,
            int contextWindowTokens, boolean recommended) {
    }
}
