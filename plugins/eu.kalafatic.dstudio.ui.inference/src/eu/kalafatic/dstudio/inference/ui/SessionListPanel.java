package eu.kalafatic.dstudio.inference.ui;

import eu.kalafatic.dstudio.inference.ui.model.SessionInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Session navigator panel: searchable session list with New/Open/Rename/Delete
 * actions. Purely presentational; every action delegates to the backend.
 */
public final class SessionListPanel {

    /** Callbacks into the workspace composite/backend. */
    public interface Listener {
        void onNewSession();

        void onOpen(String sessionId);

        void onRename(String sessionId, String newTitle);

        void onDelete(String sessionId);

        void onRefresh();
    }

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Table table;
    private final List<SessionInfo> sessions = new ArrayList<>();
    private String activeSessionId;
    private final Listener listener;
    private org.eclipse.swt.widgets.Text filter;

    public SessionListPanel(Composite parent, Listener listener) {
        this.listener = listener;
        parent.setLayout(new GridLayout(1, false));

        Composite actions = new Composite(parent, SWT.NONE);
        GridLayout actionsLayout = new GridLayout(4, false);
        actionsLayout.marginHeight = 0;
        actionsLayout.marginWidth = 0;
        actionsLayout.horizontalSpacing = 4;
        actions.setLayout(actionsLayout);
        actions.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        Button newButton = new Button(actions, SWT.PUSH);
        newButton.setText("New");
        newButton.setToolTipText("Start a new conversation session");
        newButton.addListener(SWT.Selection, event -> listener.onNewSession());

        Button rename = new Button(actions, SWT.PUSH);
        rename.setText("Rename");
        rename.setToolTipText("Rename the selected session");
        rename.addListener(SWT.Selection, event -> renameSelected());

        Button delete = new Button(actions, SWT.PUSH);
        delete.setText("Delete");
        delete.setToolTipText("Delete the selected session and its history");
        delete.addListener(SWT.Selection, event -> deleteSelected());

        Button refresh = new Button(actions, SWT.PUSH);
        refresh.setText("Refresh");
        refresh.setToolTipText("Reload the session list");
        refresh.addListener(SWT.Selection, event -> listener.onRefresh());

        filter = new org.eclipse.swt.widgets.Text(parent, SWT.SEARCH | SWT.ICON_CANCEL
                | SWT.ICON_SEARCH | SWT.BORDER);
        filter.setMessage("Search sessions\u2026");
        filter.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        filter.addModifyListener(event -> applyFilter());

        table = new Table(parent, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        table.setHeaderVisible(true);
        table.setLinesVisible(false);
        TableColumn title = new TableColumn(table, SWT.NONE);
        title.setText("Session");
        title.setWidth(150);
        TableColumn state = new TableColumn(table, SWT.NONE);
        state.setText("State");
        state.setWidth(90);
        table.addListener(SWT.MouseDoubleClick, event -> openSelected());
        table.addListener(SWT.Selection, event -> {
            // single click: keep selection only; open on double-click
        });
    }

    private void applyFilter() {
        String needle = filter.getText().strip().toLowerCase();
        table.removeAll();
        for (SessionInfo session : sessions) {
            if (!needle.isEmpty() && !session.title().toLowerCase().contains(needle)) {
                continue;
            }
            addItem(session);
        }
    }

    private void addItem(SessionInfo session) {
        TableItem item = new TableItem(table, SWT.NONE);
        item.setText(0, session.title());
        item.setText(1, session.state());
        item.setData(session.id());
        if (session.id().equals(activeSessionId)) {
            item.setForeground(0, table.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN));
            item.setFont(item.getFont());
        }
    }

    /** Replaces the list content. */
    public void setSessions(List<SessionInfo> sessions, String activeSessionId) {
        this.sessions.clear();
        if (sessions != null) {
            this.sessions.addAll(sessions);
        }
        this.sessions.sort(Comparator.comparing(SessionInfo::updatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        this.activeSessionId = activeSessionId;
        applyFilter();
    }

    public String selectedSessionId() {
        int index = table.getSelectionIndex();
        if (index < 0) {
            return null;
        }
        return (String) table.getItem(index).getData();
    }

    private void openSelected() {
        String id = selectedSessionId();
        if (id != null) {
            listener.onOpen(id);
        }
    }

    private void renameSelected() {
        String id = selectedSessionId();
        if (id == null) {
            return;
        }
        SessionInfo current = sessions.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElse(null);
        if (current == null) {
            return;
        }
        org.eclipse.jface.dialogs.InputDialog dialog =
                new org.eclipse.jface.dialogs.InputDialog(table.getShell(),
                        "Rename session", "New title:", current.title(), value -> {
                            if (value == null || value.isBlank()) {
                                return "Title must not be blank";
                            }
                            return null;
                        });
        if (dialog.open() == org.eclipse.jface.window.Window.OK) {
            listener.onRename(id, dialog.getValue().strip());
        }
    }

    private void deleteSelected() {
        String id = selectedSessionId();
        if (id == null) {
            return;
        }
        SessionInfo current = sessions.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElse(null);
        boolean confirmed = org.eclipse.jface.dialogs.MessageDialog.openConfirm(
                table.getShell(), "Delete session",
                "Delete session '" + (current == null ? id : current.title())
                        + "' and its full history?");
        if (confirmed) {
            listener.onDelete(id);
        }
    }

    public Table table() {
        return table;
    }
}
