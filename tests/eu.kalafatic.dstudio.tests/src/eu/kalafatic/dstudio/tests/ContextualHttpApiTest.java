package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.contextual.DefaultContextualActionRegistry;
import eu.kalafatic.dstudio.core.contextual.handlers.CoreContextualActions;
import eu.kalafatic.dstudio.core.contextual.providers.HarnessContextActionProvider;
import eu.kalafatic.dstudio.core.contextual.providers.ProviderContextActionProvider;
import eu.kalafatic.dstudio.core.harness.DefaultHarnessRegistry;
import eu.kalafatic.dstudio.core.inference.DefaultInferenceEngineRegistry;
import eu.kalafatic.dstudio.infrastructure.persistence.SchemaMigrationService;
import eu.kalafatic.dstudio.infrastructure.persistence.SecretCodec;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteDatabaseManager;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteHarnessStore;
import eu.kalafatic.dstudio.infrastructure.persistence.SqliteInferenceEngineStore;
import eu.kalafatic.dstudio.teip.server.ContextualHttpHandler;
import eu.kalafatic.dstudio.teip.server.DStudioServer;

/**
 * Cross-surface verification: the SAME action ids resolved and executed through the
 * web REST surface must produce the same domain results as the RCP menus (contract v1.0,
 * §12). This test drives the web surface end-to-end and asserts on the real domain
 * registries afterwards.
 */
class ContextualHttpApiTest {

    @TempDir
    Path tempDir;

    private SqliteDatabaseManager databaseManager;
    private DefaultHarnessRegistry harnessRegistry;
    private DefaultInferenceEngineRegistry engineRegistry;
    private ContextualActionRegistry registry;
    private DStudioServer server;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new SqliteDatabaseManager(tempDir.resolve("ctx-api.db"));
        new SchemaMigrationService().migrate(databaseManager.connection());
        harnessRegistry = new DefaultHarnessRegistry(new SqliteHarnessStore(
                databaseManager.connection(), new SecretCodec(tempDir)));
        harnessRegistry.registerAdapter(new FakeHarnessAdapter("opencode", "CLI"));
        engineRegistry = new DefaultInferenceEngineRegistry(new SqliteInferenceEngineStore(
                databaseManager.connection(), new SecretCodec(tempDir)));
        engineRegistry.registerEngine(new FakeInferenceEngineAdapter("enr", "ENR"));
        engineRegistry.registerEngine(new FakeInferenceEngineAdapter("ollama", "OLLAMA"));
        engineRegistry.selectDefaultEngine("enr");

        registry = new DefaultContextualActionRegistry();
        registry.registerProvider(new HarnessContextActionProvider(harnessRegistry));
        registry.registerProvider(new ProviderContextActionProvider(engineRegistry));
        CoreContextualActions.install(registry, harnessRegistry, engineRegistry, null,
                null, null, null, null);

        server = new DStudioServer("127.0.0.1", 0, harnessRegistry, null, null,
                engineRegistry, exchange -> exchange.close(), true, null, null, null,
                registry, null, null);
        server.start();
        port = server.getBoundPort();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        databaseManager.close();
    }

    private HttpResponse<String> send(String method, String path, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HttpClient.newHttpClient().send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void harnessActionsResolvedOverWeb() throws Exception {
        HttpResponse<String> response = send("GET", ContextualHttpHandler.PATH
                + "/actions?type=harness&id=opencode", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"actionId\":\"eu.kalafatic.contextual."
                + "harness.set-default\""));
        assertTrue(response.body().contains("\"enabled\":true"),
                "non-default available harness must allow set-default");
        assertFalse(response.body().contains("\"lifecycle:start\""),
                "no raw capability leaking into the action list");
    }

    @Test
    void setDefaultExecutionChangesDomainStateOverWeb() throws Exception {
        HttpResponse<String> response = send("POST", ContextualHttpHandler.PATH
                + "/execute", "{\"actionId\":\"eu.kalafatic.contextual.harness.set-default\","
                        + "\"objectType\":\"harness\",\"objectId\":\"opencode\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ok\":true"));
        assertEquals("opencode",
                harnessRegistry.getSelectedDefaultHarnessId().orElseThrow(),
                "web execution must change the same domain state as RCP");
    }

    @Test
    void disabledActionIsRefusedWithStableErrorCode() throws Exception {
        // ollama is not the default; set-default is enabled. Use remove on the
        // default engine instead: it must be disabled over the web as in RCP.
        HttpResponse<String> response = send("POST", ContextualHttpHandler.PATH
                + "/execute", "{\"actionId\":\"eu.kalafatic.contextual.provider.remove\","
                        + "\"objectType\":\"provider\",\"objectId\":\"enr\"}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"ok\":false"));
        assertTrue(response.body().contains("\"error\":\"ACTION_DISABLED\""));
        assertTrue(engineRegistry.findEngine("enr").isPresent(),
                "the disabled action must not have executed");
    }

    @Test
    void providerScanAndDiscoverModelsWorkOverWeb() throws Exception {
        HttpResponse<String> scan = send("POST", ContextualHttpHandler.PATH + "/execute",
                "{\"actionId\":\"eu.kalafatic.contextual.provider-registry.scan\","
                        + "\"objectType\":\"provider-registry\",\"objectId\":\"\"}");
        assertEquals(200, scan.statusCode());
        assertTrue(scan.body().contains("\"ok\":true"));

        HttpResponse<String> discover = send("POST", ContextualHttpHandler.PATH
                + "/execute", "{\"actionId\":\"eu.kalafatic.contextual.provider."
                        + "discover-models\",\"objectType\":\"provider\","
                        + "\"objectId\":\"enr\"}");
        assertEquals(200, discover.statusCode());
        assertTrue(discover.body().contains("\"ok\":true"));
    }
}
