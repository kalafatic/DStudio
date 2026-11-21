package eu.kalafatic.dstudio.inference.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The prompt composer: a multiline input (Enter sends, Shift+Enter inserts a newline),
 * attachment support and Send/Stop/Clear controls. Never blocks; all callbacks are
 * fire-and-forget to the backend.
 */
public final class InputComposer {

    /** Callbacks into the workspace composite/backend. */
    public interface Listener {
        void onSend(String text, List<String> attachmentPaths);

        void onStop();

        /** Invoked by the optional "Run task" action (defaults to onSend). */
        default void onSendTask(String text, List<String> attachmentPaths) {
            onSend(text, attachmentPaths);
        }
    }

    private final Composite parent;
    private final Text input;
    private final Button sendButton;
    private final Button stopButton;
    private final Button taskButton;
    private final Label attachmentLabel;
    private final List<String> attachments = new ArrayList<>();
    private final Listener listener;
    private boolean busy;

    public InputComposer(Composite parent, Listener listener) {
        this.parent = parent;
        this.listener = listener;

        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 6;
        layout.marginWidth = 6;
        layout.verticalSpacing = 4;
        parent.setLayout(layout);

        input = new Text(parent, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL | SWT.BORDER);
        GridData inputData = new GridData(SWT.FILL, SWT.FILL, true, true);
        inputData.heightHint = 72;
        inputData.minimumHeight = 40;
        input.setLayoutData(inputData);
        input.setMessage("Enter a prompt \u2014 Enter sends, Shift+Enter adds a new line");
        input.addKeyListener(new org.eclipse.swt.events.KeyListener() {
            @Override
            public void keyPressed(org.eclipse.swt.events.KeyEvent event) {
                if (event.keyCode == SWT.CR && (event.stateMask & SWT.MODIFIER_MASK) == 0) {
                    // Plain Enter sends; Shift+Enter falls through and inserts a newline.
                    event.doit = false;
                    send();
                }
            }

            @Override
            public void keyReleased(org.eclipse.swt.events.KeyEvent event) {
                // nothing
            }
        });

        Composite buttons = new Composite(parent, SWT.NONE);
        GridLayout buttonsLayout = new GridLayout(6, false);
        buttonsLayout.marginHeight = 0;
        buttonsLayout.marginWidth = 0;
        buttonsLayout.horizontalSpacing = 6;
        buttons.setLayout(buttonsLayout);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        Button attach = new Button(buttons, SWT.PUSH);
        attach.setText("Attach\u2026");
        attach.setToolTipText("Attach local files; their content is included with the prompt");
        attach.addListener(SWT.Selection, event -> chooseFiles());

        Button clear = new Button(buttons, SWT.PUSH);
        clear.setText("Clear");
        clear.setToolTipText("Clear the input and attachments");
        clear.addListener(SWT.Selection, event -> {
            input.setText("");
            attachments.clear();
            renderAttachments();
            input.setFocus();
        });

        attachmentLabel = new Label(buttons, SWT.NONE);
        attachmentLabel.setForeground(parent.getDisplay().getSystemColor(
                SWT.COLOR_DARK_GRAY));
        GridData attachmentData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        attachmentLabel.setLayoutData(attachmentData);

        taskButton = new Button(buttons, SWT.PUSH);
        taskButton.setText("Run task");
        taskButton.setToolTipText("Run this prompt as a coding task through the selected"
                + " execution backend");
        taskButton.setLayoutData(new GridData());
        taskButton.setVisible(false);
        ((GridData) taskButton.getLayoutData()).exclude = true;
        taskButton.addListener(SWT.Selection, event -> sendTask());

        stopButton = new Button(buttons, SWT.PUSH);
        stopButton.setText("Stop");
        stopButton.setToolTipText("Stop the current generation");
        stopButton.setEnabled(false);
        stopButton.addListener(SWT.Selection, event -> {
            stopButton.setEnabled(false);
            listener.onStop();
        });

        sendButton = new Button(buttons, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.setToolTipText("Send the prompt (Enter)");
        GridData sendData = new GridData();
        sendData.widthHint = 90;
        sendButton.setLayoutData(sendData);
        sendButton.addListener(SWT.Selection, event -> send());

    }

    private void send() {
        sendInternal(false);
    }

    private void sendTask() {
        sendInternal(true);
    }

    private void sendInternal(boolean task) {
        String text = input.getText().strip();
        if (text.isEmpty()) {
            input.setFocus();
            return;
        }
        if (busy) {
            return;
        }
        List<String> paths = List.copyOf(attachments);
        input.setText("");
        attachments.clear();
        renderAttachments();
        if (task) {
            listener.onSendTask(text, paths);
        } else {
            listener.onSend(text, paths);
        }
    }

    private void chooseFiles() {
        FileDialog dialog = new FileDialog(parent.getShell(), SWT.OPEN | SWT.MULTI);
        dialog.setText("Attach files to the prompt");
        String result = dialog.open();
        if (result == null) {
            return;
        }
        String filter = dialog.getFilterPath();
        String[] names = dialog.getFileNames();
        List<String> paths = new ArrayList<>();
        for (String name : names) {
            String path = filter.endsWith(java.io.File.separator)
                    ? filter + name : filter + java.io.File.separator + name;
            if (!attachments.contains(path)) {
                attachments.add(path);
            }
            paths.add(path);
        }
        renderAttachments();
    }

    private void renderAttachments() {
        if (attachments.isEmpty()) {
            attachmentLabel.setText("");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (String path : attachments) {
            if (names.length() > 0) {
                names.append("   ");
            }
            names.append("\uD83D\uDCCE ").append(
                    java.nio.file.Path.of(path).getFileName());
        }
        attachmentLabel.setText(names.toString());
        attachmentLabel.setToolTipText(String.join("\n", attachments));
    }

    /** Switches between Send and Stop depending on generation state. */
    public void setBusy(boolean busy) {
        this.busy = busy;
        sendButton.setEnabled(!busy);
        stopButton.setEnabled(busy);
    }

    /** Displays attachment names reported by the backend. */
    public void showAttachments(List<String> names) {
        if (names == null || names.isEmpty()) {
            attachmentLabel.setText("");
            return;
        }
        attachmentLabel.setText(String.join("   ", names));
    }

    /** Shows or hides the "Run task" action. */
    public void setTaskButtonVisible(boolean visible) {
        if (taskButton.getVisible() == visible) {
            return;
        }
        taskButton.setVisible(visible);
        ((GridData) taskButton.getLayoutData()).exclude = !visible;
        taskButton.getParent().layout(true, true);
    }

    public boolean isBusy() {
        return busy;
    }

    public void setFocus() {
        input.setFocus();
    }
}
