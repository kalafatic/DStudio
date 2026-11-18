package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface HarnessStore {

    void saveDescriptor(HarnessDescriptor descriptor);

    void deleteDescriptor(String harnessId);

    Optional<HarnessDescriptor> findDescriptor(String harnessId);

    List<HarnessDescriptor> listDescriptors();

    void saveSelectedHarnessId(String harnessId);

    Optional<String> findSelectedHarnessId();

    void clearSelectedHarnessId();

    void saveConfiguration(String harnessId, Map<String, String> configuration);

    Map<String, String> findConfiguration(String harnessId);
}
