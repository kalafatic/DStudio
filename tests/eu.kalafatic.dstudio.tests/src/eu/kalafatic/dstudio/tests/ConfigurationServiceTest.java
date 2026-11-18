package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.config.DStudioConfiguration;
import eu.kalafatic.dstudio.infrastructure.config.DStudioConfigurationServiceImpl;
import eu.kalafatic.dstudio.infrastructure.config.DStudioPaths;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsAreLoadedWhenNothingConfigured() {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("cfg-default"));
        DStudioConfigurationServiceImpl service =
                new DStudioConfigurationServiceImpl(paths);
        DStudioConfiguration configuration = service.getConfiguration();
        assertEquals(DStudioConfiguration.DEFAULT_TEIP_PORT, configuration.teipPort());
        assertEquals("127.0.0.1", configuration.teipBindAddress());
        assertEquals(DStudioConfiguration.NETWORK_LOOPBACK,
                configuration.teipNetworkMode());
        assertFalse(configuration.teipDevMode());
    }

    @Test
    void saveAndReloadRoundTrip() {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("cfg-roundtrip"));
        DStudioConfigurationServiceImpl service =
                new DStudioConfigurationServiceImpl(paths);
        DStudioConfiguration custom = new DStudioConfiguration(
                DStudioConfiguration.APP_NAME, DStudioConfiguration.APP_VERSION,
                paths.getConfigurationDirectory(), "0.0.0.0", 48999,
                DStudioConfiguration.NETWORK_LAN, 48199, true, "opencode",
                "ollama/test:latest", 900, "", "");
        service.saveConfiguration(custom);
        DStudioConfigurationServiceImpl reloaded =
                new DStudioConfigurationServiceImpl(paths);
        DStudioConfiguration loaded = reloaded.getConfiguration();
        assertEquals(48999, loaded.teipPort());
        assertEquals("0.0.0.0", loaded.teipBindAddress());
        assertEquals(DStudioConfiguration.NETWORK_LAN, loaded.teipNetworkMode());
        assertTrue(loaded.teipDevMode());
        assertEquals("opencode", loaded.defaultHarnessId());
        assertEquals("ollama/test:latest", loaded.defaultModel());
        assertEquals(900, loaded.executionTimeoutSeconds());
    }

    @Test
    void systemPropertyOverridesFileValue() {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("cfg-precedence"));
        DStudioConfigurationServiceImpl service =
                new DStudioConfigurationServiceImpl(paths);
        service.saveConfiguration(DStudioConfiguration.defaults(
                paths.getConfigurationDirectory()));
        System.setProperty(DStudioConfigurationServiceImpl.SYS_TEIP_PORT, "49980");
        try {
            DStudioConfigurationServiceImpl fresh =
                    new DStudioConfigurationServiceImpl(paths);
            assertEquals(49980, fresh.getConfiguration().teipPort());
        } finally {
            System.clearProperty(DStudioConfigurationServiceImpl.SYS_TEIP_PORT);
        }
    }

    @Test
    void invalidNumericValueFallsBackToDefault() {
        DStudioPaths paths = new DStudioPaths(tempDir.resolve("cfg-invalid"));
        System.setProperty(DStudioConfigurationServiceImpl.SYS_TEIP_PORT, "not-a-number");
        try {
            DStudioConfigurationServiceImpl service =
                    new DStudioConfigurationServiceImpl(paths);
            assertEquals(DStudioConfiguration.DEFAULT_TEIP_PORT,
                    service.getConfiguration().teipPort());
        } finally {
            System.clearProperty(DStudioConfigurationServiceImpl.SYS_TEIP_PORT);
        }
    }
}
