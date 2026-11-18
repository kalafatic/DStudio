package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.contextual.DefaultContextualActionRegistry;
import eu.kalafatic.dstudio.core.contextual.handlers.CoreContextualActions;
import eu.kalafatic.dstudio.core.contextual.providers.HarnessContextActionProvider;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.harness.HarnessRegistry;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;

/**
 * Harness contextual actions (contract v1.0): availability gating on default/state and
 * lifecycle capabilities, and real domain execution through the shared registry.
 */
class HarnessContextActionsTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private HarnessRegistry harnessRegistry;
    private ContextualActionRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("ctx-harness.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        harnessRegistry = new DefaultHarnessRegistry(
                new SqliteHarnessStore(databaseManager.connection(),
                        new SecretCodec(tempDir)));
        harnessRegistry.registerAdapter(new FakeHarnessAdapter("fake", "FAKE"));
        registry = new DefaultContextualActionRegistry();
        registry.registerProvider(new HarnessContextActionProvider(harnessRegistry));
        CoreContextualActions.install(registry, harnessRegistry, null, null, null, null,
                null, null);
    }

    @AfterEach
    void tearDown() {
        databaseManager.close();
    }

    private ContextSelection harnessSelection(String id) {
        return ContextSelection.builder("DSTUDIO", "RCP", "harness").object(id, id)
                .state("enabled", harnessRegistry.findHarness(id).orElseThrow().isEnabled())
                .state("isDefault", harnessRegistry.getSelectedDefaultHarnessId()
                        .map(id::equals).orElse(false))
                .capabilities(harnessRegistry.findHarness(id).orElseThrow()
                        .getCapabilities())
                .build();
    }

    @Test
    void setDefaultEnabledOnlyForNonDefaultHarnessAndExecutesRealChange() {
        ContextualAction action = registry.resolve(ContextActionIds.HARNESS_SET_DEFAULT,
                harnessSelection("fake")).orElseThrow();
        assertTrue(action.enabled());
        assertTrue(registry.execute(ContextActionIds.HARNESS_SET_DEFAULT,
                harnessSelection("fake")).ok());
        assertEquals("fake",
                harnessRegistry.getSelectedDefaultHarnessId().orElseThrow(),
                "execution must change the real domain selection");

        ContextualAction after = registry.resolve(ContextActionIds.HARNESS_SET_DEFAULT,
                harnessSelection("fake")).orElseThrow();
        assertFalse(after.enabled(), "default harness cannot be set default again");
        assertTrue(after.disabledReason().contains("default"));
    }

    @Test
    void lifecycleActionsDisabledWhenHarnessLacksCapabilities() {
        ContextualAction start = registry.resolve(ContextActionIds.HARNESS_START,
                harnessSelection("fake")).orElseThrow();
        assertFalse(start.enabled());
        assertTrue(start.disabledReason().contains("lifecycle"),
                "reason must explain the missing capability");

        ContextualAction logs = registry.resolve(ContextActionIds.HARNESS_VIEW_LOGS,
                harnessSelection("fake")).orElseThrow();
        assertFalse(logs.enabled());
    }

    @Test
    void removeDisabledForDefaultHarnessAndExecutesForOther() {
        harnessRegistry.registerAdapter(new FakeHarnessAdapter("other", "FAKE"));
        harnessRegistry.selectDefaultHarness("fake");

        ContextualAction removeDefault = registry.resolve(ContextActionIds.HARNESS_REMOVE,
                harnessSelection("fake")).orElseThrow();
        assertFalse(removeDefault.enabled());

        ContextualAction removeOther = registry.resolve(ContextActionIds.HARNESS_REMOVE,
                harnessSelection("other")).orElseThrow();
        assertTrue(removeOther.enabled());
        assertTrue(registry.execute(ContextActionIds.HARNESS_REMOVE,
                harnessSelection("other")).ok());
        assertTrue(harnessRegistry.findHarness("other").isEmpty(),
                "remove must unregister the real harness");
    }

    @Test
    void scanTestsEveryHarnessAndUpdatesStatuses() {
        ContextualAction scan = registry.resolve(ContextActionIds.HARNESS_REGISTRY_SCAN,
                ContextSelection.builder("DSTUDIO", "RCP", "harness-registry").build())
                .orElseThrow();
        assertTrue(scan.enabled());
        assertTrue(registry.execute(ContextActionIds.HARNESS_REGISTRY_SCAN,
                ContextSelection.builder("DSTUDIO", "RCP", "harness-registry").build())
                .ok());
    }

    @Test
    void testConnectionUsesRealRegistryTest() {
        assertTrue(registry.execute(ContextActionIds.HARNESS_TEST,
                harnessSelection("fake")).ok());
        assertEquals(Optional.of(eu.kalafatic.dstudio.core.model.HarnessDescriptor
                .HarnessStatus.CONNECTED), harnessRegistry.findHarness("fake")
                .map(d -> d.getStatus()), "test connection must update the live status");
    }
}
