package eu.kalafatic.dstudio.infrastructure.config;

import java.nio.file.Path;

public final class DStudioPaths {

    private final Path configurationDirectory;

    public DStudioPaths() {
        this(defaultConfigurationDirectory());
    }

    public DStudioPaths(Path configurationDirectory) {
        this.configurationDirectory = configurationDirectory.toAbsolutePath().normalize();
    }

    public static Path defaultConfigurationDirectory() {
        String override = System.getProperty("dstudio.config.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home", ".")).resolve(".dstudio");
    }

    public Path getConfigurationDirectory() {
        return configurationDirectory;
    }

    public Path getDatabaseFile() {
        return configurationDirectory.resolve("dstudio.db");
    }

    public Path getPropertiesFile() {
        return configurationDirectory.resolve("dstudio.properties");
    }

    public Path getHarnessRunsDirectory() {
        return configurationDirectory.resolve("harness-runs");
    }
}
