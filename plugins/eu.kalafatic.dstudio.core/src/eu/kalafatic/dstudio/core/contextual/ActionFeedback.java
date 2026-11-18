package eu.kalafatic.dstudio.core.contextual;

/**
 * Execution feedback channel used by handlers to report progress and outcomes without
 * knowing the rendering surface. RCP surfaces translate to dialogs/status; Web surfaces
 * translate to JSON responses and notifications.
 */
public interface ActionFeedback {

    void progress(String message);

    void info(String message);

    void error(String message, Throwable cause);

    static ActionFeedback silent() {
        return new ActionFeedback() {
            @Override
            public void progress(String message) {
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void error(String message, Throwable cause) {
            }
        };
    }
}
