package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.harness.HarnessConnectionTestResult;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.core.model.HarnessDescriptor;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultHarnessRegistryTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("registry-test.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    private HarnessRegistry newRegistry() {
        return new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                new SecretCodec(tempDir)));
    }

    @Test
    void registerCreatesEnabledDescriptorAndPersists() {
        HarnessRegistry registry = newRegistry();
        FakeHarnessAdapter adapter = new FakeHarnessAdapter("fake", "FAKE");
        registry.registerAdapter(adapter);
        List<HarnessDescriptor> harnesses = registry.listHarnesses();
        assertEquals(1, harnesses.size());
        HarnessDescriptor descriptor = harnesses.get(0);
        assertEquals("fake", descriptor.getId());
        assertEquals("FAKE", descriptor.getType());
        assertTrue(descriptor.isEnabled());
        assertTrue(descriptor.getCapabilities().contains("CODE_IMPLEMENTATION"));
        HarnessRegistry second = newRegistry();
        assertTrue(second.findHarness("fake").isPresent());
    }

    @Test
    void selectionPersistsAcrossRegistryInstances() {
        HarnessRegistry registry = newRegistry();
        registry.registerAdapter(new FakeHarnessAdapter("fake", "FAKE"));
        registry.selectDefaultHarness("fake");
        HarnessRegistry second = newRegistry();
        assertTrue(second.getSelectedDefaultHarnessId().isPresent());
        assertEquals("fake", second.getSelectedDefaultHarnessId().get());
        second.selectDefaultHarness("");
        assertTrue(second.getSelectedDefaultHarnessId().isEmpty());
        HarnessRegistry third = newRegistry();
        assertTrue(third.getSelectedDefaultHarnessId().isEmpty());
    }

    @Test
    void configurationPersistsAndIsAppliedOnRegister() {
        HarnessRegistry registry = newRegistry();
        FakeHarnessAdapter adapter = new FakeHarnessAdapter("fake", "FAKE");
        registry.registerAdapter(adapter);
        registry.updateConfiguration("fake",
                Map.of("binary", "/custom/opencode", "model", "ollama/test:latest"));
        HarnessRegistry second = newRegistry();
        FakeHarnessAdapter freshAdapter = new FakeHarnessAdapter("fake", "FAKE");
        second.registerAdapter(freshAdapter);
        assertEquals("/custom/opencode",
                freshAdapter.getConnectionConfiguration().get("binary"));
        assertEquals("ollama/test:latest",
                freshAdapter.getConnectionConfiguration().get("model"));
    }

    @Test
    void configurationUpdatePreservesUnspecifiedKeys() {
        HarnessRegistry registry = newRegistry();
        registry.registerAdapter(new FakeHarnessAdapter("fake", "FAKE"));
        registry.updateConfiguration("fake", Map.of("binary", "/custom/opencode",
                "authToken", "secret-token"));
        // A partial update (as produced by the configuration dialog when it skips a
        // masked secret) must keep the previously stored value, not delete it.
        registry.updateConfiguration("fake", Map.of("binary", "/custom/other"));
        HarnessRegistry second = newRegistry();
        FakeHarnessAdapter freshAdapter = new FakeHarnessAdapter("fake", "FAKE");
        second.registerAdapter(freshAdapter);
        assertEquals("/custom/other",
                freshAdapter.getConnectionConfiguration().get("binary"));
        assertEquals("secret-token",
                freshAdapter.getConnectionConfiguration().get("authToken"));
    }

    @Test
    void disabledAdapterIsNotSelected() {
        HarnessRegistry registry = newRegistry();
        registry.registerAdapter(new FakeHarnessAdapter("fake", "FAKE"));
        registry.selectDefaultHarness("fake");
        assertTrue(registry.findSelectedDefaultAdapter().isPresent());
        registry.setHarnessEnabled("fake", false);
        assertTrue(registry.findSelectedDefaultAdapter().isEmpty());
        registry.setHarnessEnabled("fake", true);
        assertTrue(registry.findSelectedDefaultAdapter().isPresent());
    }

    @Test
    void unknownHarnessOperationsFail() {
        HarnessRegistry registry = newRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.selectDefaultHarness("missing"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.setHarnessEnabled("missing", true));
        assertThrows(IllegalArgumentException.class,
                () -> registry.updateConfiguration("missing", Map.of()));
        HarnessConnectionTestResult result = registry.testConnection("missing");
        assertFalse(result.success());
    }

    @Test
    void testConnectionUpdatesDescriptorStatus() {
        HarnessRegistry registry = newRegistry();
        FakeHarnessAdapter adapter = new FakeHarnessAdapter("fake", "FAKE");
        adapter.setConnectionResult(HarnessConnectionTestResult.error("binary missing"));
        registry.registerAdapter(adapter);
        registry.testConnection("fake");
        assertEquals(HarnessDescriptor.HarnessStatus.DISCONNECTED,
                registry.findHarness("fake").get().getStatus());
        adapter.setConnectionResult(HarnessConnectionTestResult.ok("ready"));
        registry.testConnection("fake");
        assertEquals(HarnessDescriptor.HarnessStatus.CONNECTED,
                registry.findHarness("fake").get().getStatus());
    }

    @Test
    void capabilityAggregationHonorsEnabledFlag() {
        HarnessRegistry registry = newRegistry();
        registry.registerAdapter(new FakeHarnessAdapter("fake", "FAKE"));
        assertEquals(1, registry.aggregateCapabilities().size());
        registry.setHarnessEnabled("fake", false);
        assertTrue(registry.aggregateCapabilities().isEmpty());
    }
}
