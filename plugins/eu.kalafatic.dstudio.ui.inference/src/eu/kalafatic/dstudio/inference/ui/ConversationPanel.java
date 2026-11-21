package eu.kalafatic.dstudio.inference.ui;

import eu.kalafatic.dstudio.inference.ui.model.MessageKind;
import eu.kalafatic.dstudio.inference.ui.model.StatusKind;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The conversation area: user messages, assistant responses with markdown and code
 * blocks, tool/execution output, system and error entries. Streaming entries are
 * updated incrementally and replaced by authoritative content on completion.
 */
public final class ConversationPanel {

    private static final Pattern CODE_BLOCK = Pattern.compile(
            "```[a-zA-Z0-9+\\-.]*[\\r\\n]+(.*?)```", Pattern.DOTALL);

    private final Composite parent;
    private final ScrolledComposite scroller;
    private final Composite content;
    private final MarkdownRenderer markdown;
    private final List<Entry> entries = new ArrayList<>();
    private Entry streaming;
    private int counter;

    private final Color userColor;
    private final Color assistantColor;
    private final Color toolColor;
    private final Color executionColor;
    private final Color systemColor;
    private final Color errorColor;
    private final Color busyColor;
    private final Color statusOkColor;
    private final Color userBackground;
    private final Color toolBackground;

    public ConversationPanel(Composite parent, MarkdownRenderer markdown) {
        this.parent = parent;
        this.markdown = markdown;
        Display display = parent.getDisplay();
        this.userColor = new Color(display, 0, 82, 204);
        this.assistantColor = new Color(display, 0, 120, 80);
        this.toolColor = new Color(display, 120, 84, 0);
        this.executionColor = new Color(display, 0, 128, 128);
        this.systemColor = new Color(display, 105, 105, 105);
        this.errorColor = new Color(display, 180, 0, 0);
        this.busyColor = new Color(display, 0, 102, 204);
        this.statusOkColor = new Color(display, 0, 128, 0);
        this.userBackground = new Color(display, 240, 245, 252);
        this.toolBackground = new Color(display, 248, 246, 240);

        this.scroller = new ScrolledComposite(parent, SWT.V_SCROLL);
        scroller.setExpandHorizontal(true);
        scroller.setExpandVertical(true);
        this.content = new Composite(scroller, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 6;
        layout.marginWidth = 6;
        layout.verticalSpacing = 8;
        content.setLayout(layout);
        scroller.setContent(content);
        scroller.addControlListener(ControlListener.controlResizedAdapter(
                event -> refreshMinSize()));
    }

    public ScrolledComposite control() {
        return scroller;
    }

    public void clear() {
        streaming = null;
        for (Entry entry : entries) {
            entry.row.dispose();
        }
        entries.clear();
        refreshMinSize();
    }

    public void addMessage(MessageKind kind, String author, String content) {
        Entry entry = createEntry(kind, author, false);
        entry.setContent(content);
        maybeAutoscroll();
    }

    public String addStreamingMessage(MessageKind kind, String author) {
        if (streaming != null) {
            streaming.finished = true;
            streaming = null;
        }
        Entry entry = createEntry(kind, author, true);
        streaming = entry;
        maybeAutoscroll();
        return entry.id;
    }

    public void appendToStreaming(String entryId, String delta) {
        Entry entry = find(entryId);
        if (entry == null) {
            return;
        }
        entry.append(delta);
        maybeAutoscroll();
    }

    public void finishStreaming(String entryId, String content) {
        Entry entry = find(entryId);
        if (entry == null) {
            return;
        }
        entry.setContent(content == null ? entry.raw.toString() : content);
        entry.finished = true;
        if (streaming == entry) {
            streaming = null;
        }
        maybeAutoscroll();
    }

    /** Removes a streaming entry (it was superseded by an authoritative message). */
    public void cancelStreaming(String entryId) {
        Entry entry = find(entryId);
        if (entry == null) {
            return;
        }
        entries.remove(entry);
        entry.row.dispose();
        if (streaming == entry) {
            streaming = null;
        }
        refreshMinSize();
    }

    /** True when an unfinished streaming entry exists. */
    public boolean hasActiveStreaming() {
        return streaming != null;
    }

    public String activeStreamingId() {
        return streaming != null ? streaming.id : null;
    }

    /** Compact single-line status entry. */
    public void addStatusEntry(String text, StatusKind kind) {
        Entry entry = createEntry(MessageKind.STATUS, null, false);
        entry.setStatusText(text, kind);
        maybeAutoscroll();
    }

    // ---------------------------------------------------------------- entries

    private Entry find(String entryId) {
        for (Entry entry : entries) {
            if (entry.id.equals(entryId)) {
                return entry;
            }
        }
        return null;
    }

    private Entry createEntry(MessageKind kind, String author, boolean streamingEntry) {
        Entry entry = new Entry("e" + (counter++), kind, author);
        entry.row = new Composite(content, SWT.NONE);
        GridLayout rowLayout = new GridLayout(1, false);
        rowLayout.marginHeight = 0;
        rowLayout.marginWidth = 0;
        entry.row.setLayout(rowLayout);
        entry.row.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        if (kind == MessageKind.STATUS) {
            entry.label = new Label(entry.row, SWT.WRAP);
            GridData labelData = new GridData(SWT.FILL, SWT.FILL, true, false);
            labelData.verticalIndent = 2;
            entry.label.setLayoutData(labelData);
        } else {
            Composite header = new Composite(entry.row, SWT.NONE);
            GridLayout headerLayout = new GridLayout(4, false);
            headerLayout.marginHeight = 0;
            headerLayout.marginWidth = 0;
            headerLayout.horizontalSpacing = 6;
            header.setLayout(headerLayout);
            header.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

            Label chip = new Label(header, SWT.NONE);
            chip.setText(chipText(kind));
            chip.setForeground(chipColor(kind));
            Label authorLabel = new Label(header, SWT.NONE);
            authorLabel.setText(author == null || author.isBlank() ? "" : author);
            authorLabel.setForeground(chipColor(kind));

            Button copy = new Button(header, SWT.PUSH);
            copy.setText("Copy");
            copy.setToolTipText("Copy the raw content of this message");
            copy.addListener(SWT.Selection, event -> copyToClipboard(entry.raw.toString()));

            if (kind == MessageKind.ASSISTANT || kind == MessageKind.EXECUTION
                    || kind == MessageKind.TOOL) {
                Button copyCode = new Button(header, SWT.PUSH);
                copyCode.setText("Copy code");
                copyCode.setToolTipText("Copy all fenced code blocks of this message");
                copyCode.addListener(SWT.Selection,
                        event -> copyToClipboard(extractCode(entry.raw.toString())));
            }
            GridData headerData = new GridData(SWT.FILL, SWT.FILL, true, false);
            headerData.verticalIndent = 2;

            int style = SWT.READ_ONLY | SWT.V_SCROLL | SWT.WRAP;
            boolean mono = kind == MessageKind.TOOL || kind == MessageKind.EXECUTION
                    || kind == MessageKind.ERROR;
            if (mono) {
                style = SWT.READ_ONLY | SWT.V_SCROLL;
            }
            entry.text = new StyledText(entry.row, style);
            GridData textData = new GridData(SWT.FILL, SWT.FILL, true, false);
            textData.widthHint = Math.max(400, parent.getClientArea().width - 120);
            entry.text.setLayoutData(textData);
            entry.text.setMargins(8, 6, 8, 6);
            if (kind == MessageKind.USER) {
                entry.text.setBackground(userBackground);
            }
            if (kind == MessageKind.TOOL || kind == MessageKind.EXECUTION) {
                entry.text.setBackground(toolBackground);
            }
            entry.text.setEditable(false);
            if (streamingEntry) {
                entry.text.setForeground(chipColor(kind));
            }
        }
        entries.add(entry);
        return entry;
    }

    private String chipText(MessageKind kind) {
        return switch (kind) {
            case USER -> "You";
            case ASSISTANT -> "Assistant";
            case TOOL -> "Tool";
            case EXECUTION -> "Execution";
            case SYSTEM -> "System";
            case ERROR -> "Error";
            default -> "";
        };
    }

    private Color chipColor(MessageKind kind) {
        return switch (kind) {
            case USER -> userColor;
            case ASSISTANT -> assistantColor;
            case TOOL -> toolColor;
            case EXECUTION -> executionColor;
            case SYSTEM -> systemColor;
            case ERROR -> errorColor;
            default -> systemColor;
        };
    }

    private static String extractCode(String content) {
        if (content == null) {
            return "";
        }
        Matcher matcher = CODE_BLOCK.matcher(content);
        StringBuilder result = new StringBuilder();
        boolean found = false;
        while (matcher.find()) {
            if (found) {
                result.append("\n\n");
            }
            result.append(matcher.group(1).strip());
            found = true;
        }
        return found ? result.toString() : content;
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = new Clipboard(parent.getDisplay());
        try {
            clipboard.setContents(new Object[]{text},
                    new Transfer[]{TextTransfer.getInstance()});
        } finally {
            clipboard.dispose();
        }
    }

    // ---------------------------------------------------------------- layout

    private void maybeAutoscroll() {
        refreshMinSize();
        boolean atBottom = scroller.getOrigin().y
                + scroller.getClientArea().height >= content.getSize().y - 30;
        if (atBottom) {
            content.getDisplay().asyncExec(() -> {
                if (!scroller.isDisposed()) {
                    refreshMinSize();
                    scroller.setOrigin(0, content.getSize().y);
                }
            });
        }
    }

    private void refreshMinSize() {
        if (content.isDisposed()) {
            return;
        }
        content.layout(true);
        scroller.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT));
    }

    public void dispose() {
        userColor.dispose();
        assistantColor.dispose();
        toolColor.dispose();
        executionColor.dispose();
        systemColor.dispose();
        errorColor.dispose();
        busyColor.dispose();
        statusOkColor.dispose();
        userBackground.dispose();
        toolBackground.dispose();
    }

    // ---------------------------------------------------------------- entry

    private final class Entry {
        final String id;
        final MessageKind kind;
        final String author;
        Composite row;
        StyledText text;
        Label label;
        final StringBuilder raw = new StringBuilder();
        boolean finished;

        Entry(String id, MessageKind kind, String author) {
            this.id = id;
            this.kind = kind;
            this.author = author;
        }

        void setContent(String content) {
            raw.setLength(0);
            raw.append(content == null ? "" : content);
            render();
        }

        void append(String delta) {
            raw.append(delta);
            render();
        }

        void setStatusText(String text, StatusKind statusKind) {
            label.setText("\u25AA " + text);
            label.setForeground(switch (statusKind) {
                case ERROR -> errorColor;
                case SUCCESS -> statusOkColor;
                case BUSY -> busyColor;
                default -> systemColor;
            });
        }

        private void render() {
            if (text == null) {
                return;
            }
            if (kind == MessageKind.ASSISTANT || kind == MessageKind.USER) {
                markdown.render(text, raw.toString());
            } else {
                text.setText(raw.toString());
            }
            int lines = raw.toString().split("\\n", -1).length + 2;
            ((GridData) text.getLayoutData()).heightHint = Math.min(lines, 40)
                    * text.getLineHeight() + 12;
            content.layout(true);
        }
    }
}
