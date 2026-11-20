package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.inference.DefaultInferenceEngineRegistry;
import eu.kalafatic.dstudio.core.inference.InferenceEngineRegistry;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceEngineStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

/**
 * Inference-engine registry tests (Phase 6, WP-6.7): registration, enablement,
 * configuration persistence, default selection and independent management from harnesses.
 */
class InferenceEngineRegistryTest {

    @TempDir
    Path tempDir;

    private InferenceEngineRegistry registryWithStore(String dbName) {
        SqliteDatabaseManager manager = new SqliteDatabaseManager(
                tempDir.resolve(dbName));
        new SchemaMigrationService().migrate(manager.connection());
        return new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(manager.connection(),
                        new SecretCodec(tempDir)));
    }

    @Test
    void registrationAndSelection() {
        InferenceEngineRegistry registry = registryWithStore("registry-test.db");
        FakeInferenceEngineAdapter enr = new FakeInferenceEngineAdapter("enr", "ENR");
        FakeInferenceEngineAdapter ollama = new FakeInferenceEngineAdapter("ollama", "OLLAMA");
        registry.registerEngine(enr);
        registry.registerEngine(ollama);
        assertEquals(2, registry.listEngines().size());
        assertTrue(registry.findEngine("enr").isPresent());
        assertTrue(registry.findAdapter("ollama").isPresent());
        assertNotNull(registry.findEngine("enr").get().getName());

        assertTrue(registry.getSelectedDefaultEngineId().isEmpty());
        registry.selectDefaultEngine("enr");
        assertEquals("enr", registry.getSelectedDefaultEngineId().get());
        assertTrue(registry.findSelectedDefaultAdapter().isPresent());
        assertEquals("enr", registry.findSelectedDefaultAdapter().get().getEngineId());
    }

    @Test
    void enablementAndConnectionStatus() {
        InferenceEngineRegistry registry = registryWithStore("registry-enable.db");
        FakeInferenceEngineAdapter enr = new FakeInferenceEngineAdapter("enr", "ENR");
        registry.registerEngine(enr);
        registry.selectDefaultEngine("enr");
        assertTrue(registry.findSelectedDefaultAdapter().isPresent());

        registry.testConnection("enr");
        assertEquals(
                eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor.EngineStatus.CONNECTED,
                registry.findEngine("enr").get().getStatus());

        enr.setAvailable(false);
        registry.testConnection("enr");
        assertEquals(
                eu.kalafatic.dstudio.core.inference.InferenceEngineDescriptor.EngineStatus.DISCONNECTED,
                registry.findEngine("enr").get().getStatus());

        registry.setEngineEnabled("enr", false);
        assertTrue(registry.findSelectedDefaultAdapter().isEmpty(),
                "disabled engines must not be selected");
    }

    @Test
    void configurationPersists() {
        String dbName = "registry-config.db";
        InferenceEngineRegistry registry = registryWithStore(dbName);
        registry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        registry.updateConfiguration("enr", Map.of("endpoint", "http://localhost:28780",
                "defaultModel", "llama3.2:3b"));

        // Without a live adapter the registry serves the persisted configuration,
        // proving the store round-trip (adapter instances serve their live view instead).
        SqliteDatabaseManager manager = new SqliteDatabaseManager(tempDir.resolve(dbName));
        InferenceEngineRegistry second = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(manager.connection(),
                        new SecretCodec(tempDir)));
        Map<String, String> restored = second.getConfiguration("enr");
        assertEquals("http://localhost:28780", restored.get("endpoint"));
        assertEquals("llama3.2:3b", restored.get("defaultModel"));
    }

    @Test
    void configurationUpdatePreservesUnspecifiedKeys() {
        String dbName = "registry-merge.db";
        InferenceEngineRegistry registry = registryWithStore(dbName);
        registry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        registry.updateConfiguration("enr", Map.of("endpoint", "http://localhost:28780",
                "apiKey", "secret-key"));
        // A partial update (as produced by the configuration dialog when it skips a
        // masked secret) must keep the previously stored value, not delete it.
        registry.updateConfiguration("enr", Map.of("endpoint", "http://localhost:29999"));
        SqliteDatabaseManager manager = new SqliteDatabaseManager(tempDir.resolve(dbName));
        InferenceEngineRegistry second = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(manager.connection(),
                        new SecretCodec(tempDir)));
        Map<String, String> restored = second.getConfiguration("enr");
        assertEquals("http://localhost:29999", restored.get("endpoint"));
        assertEquals("secret-key", restored.get("apiKey"));
    }

    @Test
    void selectionPersistsAcrossRegistryInstances() {
        String dbName = "registry-persist.db";
        InferenceEngineRegistry first = registryWithStore(dbName);
        first.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        first.registerEngine(new FakeInferenceEngineAdapter("ollama", "OLLAMA"));
        first.selectDefaultEngine("enr");

        SqliteDatabaseManager manager = new SqliteDatabaseManager(tempDir.resolve(dbName));
        InferenceEngineRegistry second = new DefaultInferenceEngineRegistry(
                new SqliteInferenceEngineStore(manager.connection(),
                        new SecretCodec(tempDir)));
        assertTrue(second.getSelectedDefaultEngineId().isPresent());
        assertEquals("enr", second.getSelectedDefaultEngineId().get());
        assertEquals(2, second.listEngines().size());
    }

    @Test
    void unknownEngineRejected() {
        InferenceEngineRegistry registry = registryWithStore("registry-unknown.db");
        try {
            registry.selectDefaultEngine("missing");
            assertFalse(true, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // unknown engines are rejected
        }
        try {
            registry.setEngineEnabled("missing", true);
            assertFalse(true, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // unknown engines are rejected
        }
    }
}
