package eu.kalafatic.dstudio.inference.ui;

import eu.kalafatic.dstudio.inference.ui.model.MessageKind;
import eu.kalafatic.dstudio.inference.ui.model.SearchResult;
import eu.kalafatic.dstudio.inference.ui.model.SelectOption;
import eu.kalafatic.dstudio.inference.ui.model.SelectorSpec;
import eu.kalafatic.dstudio.inference.ui.model.SessionInfo;
import eu.kalafatic.dstudio.inference.ui.model.StatusKind;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import java.util.List;

/**
 * The complete inference workspace: configuration toolbar, conversation with streaming
 * markdown, search row, prompt composer and session navigator. Backend-agnostic: the
 * product supplies a {@link ChatWorkspaceBackend} bound to its real registries and
 * execution services.
 */
public final class ChatWorkspaceComposite implements ChatWorkspaceUi {

    public static final String SEARCH_SELECTOR = "search";
    public static final String SEARCH_DISABLED = "disabled";

    private final Composite root;
    private final ChatWorkspaceBackend backend;
    private final MarkdownRenderer markdown;
    private final ConversationPanel conversation;
    private final InputComposer composer;
    private final ToolbarBuilder toolbar;
    private final SessionListPanel sessions;
    private final SearchPanel search;
    private final Label statusLabel;
    private final Color statusNone;
    private final Color statusBusy;
    private final Color statusSuccess;
    private final Color statusError;
    private StatusKind currentStatus = StatusKind.NONE;

    public ChatWorkspaceComposite(Composite parent, ChatWorkspaceBackend backend) {
        this.root = parent;
        this.backend = backend;
        Display display = parent.getDisplay();
        this.markdown = new MarkdownRenderer(display);
        this.statusNone = new Color(display, 90, 90, 90);
        this.statusBusy = new Color(display, 0, 102, 204);
        this.statusSuccess = new Color(display, 0, 128, 0);
        this.statusError = new Color(display, 180, 0, 0);

        root.setLayout(new GridLayout(1, false));

        SashForm sash = new SashForm(root, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite sessionPane = new Composite(sash, SWT.NONE);
        sessionPane.setLayout(new GridLayout(1, false));
        this.sessions = new SessionListPanel(sessionPane, new SessionListPanel.Listener() {
            @Override
            public void onNewSession() {
                backend.createSession();
            }

            @Override
            public void onOpen(String sessionId) {
                backend.openSession(sessionId);
            }

            @Override
            public void onRename(String sessionId, String newTitle) {
                backend.renameSession(sessionId, newTitle);
            }

            @Override
            public void onDelete(String sessionId) {
                backend.deleteSession(sessionId);
            }

            @Override
            public void onRefresh() {
                backend.refreshSessions();
            }
        });

        Composite main = new Composite(sash, SWT.NONE);
        GridLayout mainLayout = new GridLayout(1, false);
        mainLayout.marginHeight = 0;
        mainLayout.marginWidth = 0;
        main.setLayout(mainLayout);

        Composite toolbarRow = new Composite(main, SWT.NONE);
        GridLayout toolbarLayout = new GridLayout(2, false);
        toolbarLayout.marginHeight = 4;
        toolbarLayout.marginWidth = 6;
        toolbarLayout.horizontalSpacing = 8;
        toolbarLayout.verticalSpacing = 4;
        toolbarRow.setLayout(toolbarLayout);
        toolbarRow.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        this.toolbar = new ToolbarBuilder(toolbarRow, this::onSelectorChanged);

        statusLabel = new Label(main, SWT.NONE);
        GridData statusData = new GridData(SWT.FILL, SWT.FILL, true, false);
        statusData.horizontalIndent = 8;
        statusLabel.setLayoutData(statusData);
        statusLabel.setForeground(statusNone);
        statusLabel.setText(" ");

        this.conversation = new ConversationPanel(main, markdown);
        conversation.control().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        this.search = new SearchPanel(main, backend::runSearch);

        this.composer = new InputComposer(main, new InputComposer.Listener() {
            @Override
            public void onSend(String text, List<String> attachmentPaths) {
                backend.sendMessage(text, attachmentPaths);
            }

            @Override
            public void onSendTask(String text, List<String> attachmentPaths) {
                backend.runTask(text, attachmentPaths);
            }

            @Override
            public void onStop() {
                backend.stopGeneration();
            }
        });

        sash.setWeights(24, 76);
        backend.init(this);
    }

    private void onSelectorChanged(String selectorId, String optionId) {
        if (SEARCH_SELECTOR.equals(selectorId)) {
            search.setVisiblePanel(optionId != null && !SEARCH_DISABLED.equals(optionId));
        }
        backend.selectorChanged(selectorId, optionId);
    }

    // ------------------------------------------------------------- ChatWorkspaceUi

    @Override
    public void setSelectors(List<SelectorSpec> specs) {
        UiThread.async(() -> {
            toolbar.setSelectors(specs);
            syncSearchVisibility();
        });
    }

    @Override
    public void updateSelectorOptions(String selectorId, List<SelectOption> options,
                                      String selectedId) {
        UiThread.async(() -> {
            toolbar.updateSelector(selectorId, options, selectedId);
            if (SEARCH_SELECTOR.equals(selectorId)) {
                syncSearchVisibility();
            }
        });
    }

    @Override
    public void setSelectorVisible(String selectorId, boolean visible) {
        UiThread.async(() -> toolbar.setSelectorVisible(selectorId, visible));
    }

    @Override
    public void setSelectorEnabled(String selectorId, boolean enabled) {
        UiThread.async(() -> toolbar.setSelectorEnabled(selectorId, enabled));
    }

    @Override
    public String selectedOption(String selectorId) {
        if (Display.getCurrent() != null) {
            return toolbar.selectedOption(selectorId);
        }
        final String[] result = {null};
        UiThread.sync(() -> result[0] = toolbar.selectedOption(selectorId));
        return result[0];
    }

    @Override
    public void setStatus(String text, StatusKind kind) {
        UiThread.async(() -> {
            currentStatus = kind == null ? StatusKind.NONE : kind;
            statusLabel.setText(text == null ? " " : text);
            statusLabel.setForeground(switch (currentStatus) {
                case BUSY -> statusBusy;
                case SUCCESS -> statusSuccess;
                case ERROR -> statusError;
                default -> statusNone;
            });
        });
    }

    @Override
    public void setBusy(boolean busy) {
        UiThread.async(() -> composer.setBusy(busy));
    }

    @Override
    public void clearConversation() {
        UiThread.async(conversation::clear);
    }

    @Override
    public void addMessage(MessageKind kind, String author, String content) {
        UiThread.async(() -> conversation.addMessage(kind, author, content));
    }

    @Override
    public String addStreamingMessage(MessageKind kind, String author) {
        if (Display.getCurrent() != null) {
            return conversation.addStreamingMessage(kind, author);
        }
        final String[] id = {null};
        UiThread.sync(() -> id[0] = conversation.addStreamingMessage(kind, author));
        return id[0];
    }

    @Override
    public void appendToStreaming(String entryId, String delta) {
        UiThread.async(() -> conversation.appendToStreaming(entryId, delta));
    }

    @Override
    public void finishStreaming(String entryId, String content) {
        UiThread.async(() -> conversation.finishStreaming(entryId, content));
    }

    @Override
    public void cancelStreaming(String entryId) {
        UiThread.async(() -> conversation.cancelStreaming(entryId));
    }

    @Override
    public void addStatusEntry(String text, StatusKind kind) {
        UiThread.async(() -> conversation.addStatusEntry(text, kind));
    }

    @Override
    public void setSessions(List<SessionInfo> sessionInfos, String activeSessionId) {
        UiThread.async(() -> sessions.setSessions(sessionInfos, activeSessionId));
    }

    @Override
    public void showSearchResults(String pattern, List<SearchResult> results, String error) {
        UiThread.async(() -> search.showResults(pattern, results, error));
    }

    @Override
    public void setSearchVisible(boolean visible) {
        UiThread.async(() -> search.setVisiblePanel(visible));
    }

    @Override
    public void showAttachments(List<String> attachmentNames) {
        UiThread.async(() -> composer.showAttachments(attachmentNames));
    }

    @Override
    public void setTaskButtonVisible(boolean visible) {
        UiThread.async(() -> composer.setTaskButtonVisible(visible));
    }

    private void syncSearchVisibility() {
        String selected = toolbar.selectedOption(SEARCH_SELECTOR);
        search.setVisiblePanel(selected != null && !SEARCH_DISABLED.equals(selected));
    }

    /** Disposes presentation resources. */
    public void dispose() {
        markdown.dispose();
        conversation.dispose();
        statusNone.dispose();
        statusBusy.dispose();
        statusSuccess.dispose();
        statusError.dispose();
    }

    public void setFocus() {
        composer.setFocus();
    }
}
