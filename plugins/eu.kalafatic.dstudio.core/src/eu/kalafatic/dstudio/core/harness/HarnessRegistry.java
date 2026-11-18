package eu.kalafatic.dstudio.core.harness;

import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface HarnessRegistry {

    void registerAdapter(HarnessAdapter adapter);

    void unregisterHarness(String harnessId);

    List<HarnessDescriptor> listHarnesses();

    Optional<HarnessDescriptor> findHarness(String harnessId);

    Optional<HarnessAdapter> findAdapter(String harnessId);

    void selectDefaultHarness(String harnessId);

    Optional<String> getSelectedDefaultHarnessId();

    Optional<HarnessAdapter> findSelectedDefaultAdapter();

    void setHarnessEnabled(String harnessId, boolean enabled);

    void updateConfiguration(String harnessId, Map<String, String> configuration);

    Map<String, String> getConfiguration(String harnessId);

    HarnessConnectionTestResult testConnection(String harnessId);

    List<HarnessCapabilityDescriptor> aggregateCapabilities();

    List<HarnessCapabilityDescriptor> aggregateCapabilities(String harnessId);
}
