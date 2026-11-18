package eu.kalafatic.dstudio.core.contextual;

/**
 * Real execution for one contextual action id. Handlers are registered per surface
 * ("*" = any, "RCP" = desktop UI, "WEB" = web interface). A handler must perform the
 * actual domain operation — there are no mock handlers.
 */
public interface ContextualActionHandler {

    String actionId();

    /**
     * Surface this handler serves: {@code "*"}, {@code "RCP"} or {@code "WEB"}.
     */
    default String surface() {
        return "*";
    }

    void execute(ContextSelection selection, ActionFeedback feedback) throws Exception;
}
