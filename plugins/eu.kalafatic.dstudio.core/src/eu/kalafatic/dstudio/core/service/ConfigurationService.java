package eu.kalafatic.dstudio.core.service;

import eu.kalafatic.dstudio.core.config.DStudioConfiguration;

public interface ConfigurationService {

    DStudioConfiguration getConfiguration();

    void saveConfiguration(DStudioConfiguration configuration);
}
