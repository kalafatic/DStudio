package eu.kalafatic.dstudio.inference.ui;

import eu.kalafatic.dstudio.inference.ui.model.MessageKind;
import eu.kalafatic.dstudio.inference.ui.model.SearchResult;
import eu.kalafatic.dstudio.inference.ui.model.SelectOption;
import eu.kalafatic.dstudio.inference.ui.model.SelectorSpec;
import eu.kalafatic.dstudio.inference.ui.model.SessionInfo;
import eu.kalafatic.dstudio.inference.ui.model.StatusKind;

import java.util.List;

/**
 * Facade through which the product drives the workspace presentation. All methods are
 * safe to call from any thread: they are marshalled to the display thread when needed.
 */
public interface ChatWorkspaceUi {

    /** Replaces the full declarative selector set (toolbar combos). */
    void setSelectors(List<SelectorSpec> specs);

    /** Updates one selector's options and current selection. */
    void updateSelectorOptions(String selectorId, List<SelectOption> options,
                               String selectedId);

    void setSelectorVisible(String selectorId, boolean visible);

    void setSelectorEnabled(String selectorId, boolean enabled);

    /** The currently selected option id of a selector, or null. */
    String selectedOption(String selectorId);

    /** Sets the status line text and kind. */
    void setStatus(String text, StatusKind kind);

    /** Toggles the composer between Send and Stop. */
    void setBusy(boolean busy);

    /** Clears the conversation. */
    void clearConversation();

    /** Appends one complete message entry. */
    void addMessage(MessageKind kind, String author, String content);

    /**
     * Starts a streaming entry and returns its id; subsequent
     * {@link #appendToStreaming} calls append incrementally.
     */
    String addStreamingMessage(MessageKind kind, String author);

    void appendToStreaming(String entryId, String delta);

    /** Replaces the streaming entry content with the authoritative final content. */
    void finishStreaming(String entryId, String content);

    /** Drops a streaming entry (e.g. superseded by an authoritative message). */
    void cancelStreaming(String entryId);

    /** Compact status entry inside the conversation. */
    void addStatusEntry(String text, StatusKind kind);

    /** Replaces the session navigator content. */
    void setSessions(List<SessionInfo> sessions, String activeSessionId);

    /** Renders search results (or an error message when the search failed). */
    void showSearchResults(String pattern, List<SearchResult> results, String error);

    /** Shows or hides the search row (controlled by the search selector). */
    void setSearchVisible(boolean visible);

    /** Shows the given attachment names in the composer. */
    void showAttachments(List<String> attachmentNames);

    /** Shows or hides the "Run task" action of the composer. */
    void setTaskButtonVisible(boolean visible);
}
