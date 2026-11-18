package eu.kalafatic.dstudio.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import eu.kalafatic.dstudio.core.contextual.ActionFeedback;
import eu.kalafatic.dstudio.core.contextual.ContextActionIds;
import eu.kalafatic.dstudio.core.contextual.ContextSelection;
import eu.kalafatic.dstudio.core.contextual.ContextualAction;
import eu.kalafatic.dstudio.core.contextual.ContextualActionDescriptor;
import eu.kalafatic.dstudio.core.contextual.ContextualActionHandler;
import eu.kalafatic.dstudio.core.contextual.ContextualActionProvider;
import eu.kalafatic.dstudio.core.contextual.ContextualActionRegistry;
import eu.kalafatic.dstudio.core.contextual.ContextualExecutionResult;
import eu.kalafatic.dstudio.core.contextual.DefaultContextualActionRegistry;

/**
 * Framework-level tests for the unified contextual action registry (Darwin Family
 * Contextual Action Contract v1.0): resolution, grouping, deduplication, availability
 * gating, surface-aware handlers and the execution result contract.
 */
class ContextualActionFrameworkTest {

    private static ContextSelection selection(String objectType, String id) {
        return ContextSelection.builder("DSTUDIO", "RCP", objectType).object(id, id)
                .build();
    }

    @Test
    void resolveOrdersByGroupAndDeduplicatesProviders() {
        ContextualActionRegistry registry = new DefaultContextualActionRegistry();
        registry.registerProvider(new ContextualActionProvider() {
            @Override
            public List<String> contextTypes() {
                return List.of("harness");
            }

            @Override
            public List<ContextualAction> actionsFor(ContextSelection selection) {
                return List.of(
                        ContextualAction.enabled(ContextualActionDescriptor.of(
                                ContextActionIds.HARNESS_REMOVE, "Remove Registration",
                                ContextualActionDescriptor.GROUP_DANGER, "harness")),
                        ContextualAction.enabled(ContextualActionDescriptor.of(
                                ContextActionIds.HARNESS_TEST, "Test Connection",
                                ContextualActionDescriptor.GROUP_LIFECYCLE, "harness")));
            }
        });
        registry.registerProvider(new ContextualActionProvider() {
            @Override
            public List<String> contextTypes() {
                return List.of("harness");
            }

            @Override
            public List<ContextualAction> actionsFor(ContextSelection selection) {
                return List.of(ContextualAction.disabled(ContextualActionDescriptor.of(
                        ContextActionIds.HARNESS_TEST, "Test Connection",
                        ContextualActionDescriptor.GROUP_LIFECYCLE, "harness"),
                        "harness unavailable"));
            }
        });

        List<ContextualAction> resolved = registry.resolve(selection("harness", "h1"));
        assertEquals(2, resolved.size(), "duplicate action ids collapse to one entry");
        assertEquals(ContextActionIds.HARNESS_TEST, resolved.get(0).actionId(),
                "lifecycle group comes before danger group");
        assertTrue(resolved.get(0).enabled(), "first registered provider wins");
    }

    @Test
    void executeRejectsDisabledActionsAndMissingHandlers() {
        ContextualActionRegistry registry = new DefaultContextualActionRegistry();
        registry.registerProvider(new ContextualActionProvider() {
            @Override
            public List<String> contextTypes() {
                return List.of("project");
            }

            @Override
            public List<ContextualAction> actionsFor(ContextSelection selection) {
                return List.of(ContextualAction.disabled(ContextualActionDescriptor.of(
                        ContextActionIds.PROJECT_BUILD, "Build",
                        ContextualActionDescriptor.GROUP_LIFECYCLE, "project"),
                        "not supported by this product"));
            }
        });

        ContextualExecutionResult result = registry.execute(ContextActionIds.PROJECT_BUILD,
                selection("project", "p1"));
        assertFalse(result.ok());
        assertEquals(ContextualExecutionResult.ERROR_ACTION_DISABLED, result.errorCode());

        registry.registerHandler(handler(ContextActionIds.PROJECT_BUILD, "*", (sel, fb) -> {
        }));
        result = registry.execute(ContextActionIds.PROJECT_BUILD, selection("project", "p1"));
        assertFalse(result.ok(), "still disabled — availability gate must hold");
        assertEquals(ContextualExecutionResult.ERROR_ACTION_DISABLED, result.errorCode());
    }

    @Test
    void executeUsesSurfaceSpecificHandlerWithWildcardFallback() {
        ContextualActionRegistry registry = new DefaultContextualActionRegistry();
        registry.registerProvider(new ContextualActionProvider() {
            @Override
            public List<String> contextTypes() {
                return List.of("harness");
            }

            @Override
            public List<ContextualAction> actionsFor(ContextSelection selection) {
                return List.of(ContextualAction.enabled(ContextualActionDescriptor.of(
                        ContextActionIds.HARNESS_EDIT, "Edit Configuration",
                        ContextualActionDescriptor.GROUP_CONFIGURATION, "harness")));
            }
        });
        final StringBuilder trace = new StringBuilder();
        registry.registerHandler(handler(ContextActionIds.HARNESS_EDIT, "*", (sel, fb) ->
                trace.append("wildcard;")));
        registry.registerHandler(handler(ContextActionIds.HARNESS_EDIT, "RCP", (sel, fb) ->
                trace.append("rcp;")));
        registry.registerHandler(handler(ContextActionIds.HARNESS_EDIT, "WEB", (sel, fb) ->
                trace.append("web;")));

        registry.execute(ContextActionIds.HARNESS_EDIT, selection("harness", "h1"));
        registry.execute(ContextActionIds.HARNESS_EDIT,
                ContextSelection.builder("DSTUDIO", "WEB", "harness").object("h1", "h1")
                        .build());

        assertEquals("rcp;web;", trace.toString(),
                "surface-specific handlers preferred, wildcard as fallback");
    }

    @Test
    void executionResultCarriesStableErrorCodes() {
        ContextualActionRegistry registry = new DefaultContextualActionRegistry();
        ContextualExecutionResult missing = registry.execute(
                ContextActionIds.HARNESS_TEST, selection("harness", "unknown"));
        assertFalse(missing.ok());
        assertEquals(ContextualExecutionResult.ERROR_TARGET_NOT_FOUND, missing.errorCode());
    }

    @Test
    void handlerFailureMapsToExecutionFailedCode() {
        ContextualActionRegistry registry = new DefaultContextualActionRegistry();
        registry.registerProvider(new ContextualActionProvider() {
            @Override
            public List<String> contextTypes() {
                return List.of("harness");
            }

            @Override
            public List<ContextualAction> actionsFor(ContextSelection selection) {
                return List.of(ContextualAction.enabled(ContextualActionDescriptor.of(
                        ContextActionIds.HARNESS_TEST, "Test Connection",
                        ContextualActionDescriptor.GROUP_LIFECYCLE, "harness")));
            }
        });
        registry.registerHandler(handler(ContextActionIds.HARNESS_TEST, "*", (sel, fb) -> {
            throw new IllegalStateException("backend exploded");
        }));
        ContextualExecutionResult result = registry.execute(ContextActionIds.HARNESS_TEST,
                selection("harness", "h1"));
        assertFalse(result.ok());
        assertEquals(ContextualExecutionResult.ERROR_EXECUTION_FAILED, result.errorCode());
        assertTrue(result.message().contains("backend exploded"));
    }

    private static ContextualActionHandler handler(String actionId, String surface, Body body) {
        return new ContextualActionHandler() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String surface() {
                return surface;
            }

            @Override
            public void execute(ContextSelection selection, ActionFeedback feedback)
                    throws Exception {
                body.run(selection, feedback);
            }
        };
    }

    private interface Body {
        void run(ContextSelection selection, ActionFeedback feedback) throws Exception;
    }
}
