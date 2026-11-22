package eu.kalafatic.dstudio.inference.ui;

import eu.kalafatic.dstudio.inference.ui.model.SearchResult;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import java.util.List;

/**
 * Optional search row: executes a backend search and renders results as a compact
 * table. Visible only while the search selector is enabled; the backend decides which
 * search implementation runs.
 */
public final class SearchPanel {

    public interface Listener {
        void onSearch(String pattern);
    }

    private final Composite parent;
    private final Text pattern;
    private final Table results;
    private final Label status;
    private final Listener listener;

    public SearchPanel(Composite parent, Listener listener) {
        this.parent = parent;
        this.listener = listener;

        GridLayout layout = new GridLayout(3, false);
        layout.marginHeight = 4;
        layout.marginWidth = 6;
        layout.horizontalSpacing = 6;
        parent.setLayout(layout);

        Label label = new Label(parent, SWT.NONE);
        label.setText("Search:");
        pattern = new Text(parent, SWT.BORDER);
        pattern.setMessage("Regex pattern\u2026");
        pattern.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        pattern.addTraverseListener(event -> {
            if (event.detail == SWT.TRAVERSE_RETURN) {
                event.doit = false;
                runSearch();
            }
        });
        Button run = new Button(parent, SWT.PUSH);
        run.setText("Run");
        run.addListener(SWT.Selection, event -> runSearch());

        results = new Table(parent, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER
                | SWT.V_SCROLL);
        GridData resultsData = new GridData(SWT.FILL, SWT.FILL, true, false);
        resultsData.horizontalSpan = 3;
        resultsData.heightHint = 140;
        results.setLayoutData(resultsData);
        results.setHeaderVisible(true);
        TableColumn location = new TableColumn(results, SWT.NONE);
        location.setText("File");
        location.setWidth(220);
        TableColumn line = new TableColumn(results, SWT.NONE);
        line.setText("Line");
        line.setWidth(50);
        TableColumn text = new TableColumn(results, SWT.NONE);
        text.setText("Match");
        text.setWidth(420);

        status = new Label(parent, SWT.WRAP);
        GridData statusData = new GridData(SWT.FILL, SWT.FILL, true, false);
        statusData.horizontalSpan = 3;
        status.setLayoutData(statusData);
        status.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        parent.setVisible(false);
        GridData parentData = parent.getLayoutData() instanceof GridData existing
                ? existing : new GridData();
        parentData.exclude = true;
        parent.setLayoutData(parentData);
    }

    private void runSearch() {
        String value = pattern.getText().strip();
        if (value.isEmpty()) {
            return;
        }
        listener.onSearch(value);
    }

    /** Renders results (or an error message). */
    public void showResults(String query, List<SearchResult> results, String error) {
        this.results.removeAll();
        if (error != null && !error.isBlank()) {
            status.setText("Search failed: " + error);
            return;
        }
        if (results == null || results.isEmpty()) {
            status.setText("No matches for '" + query + "'");
            return;
        }
        for (SearchResult result : results) {
            TableItem item = new TableItem(this.results, SWT.NONE);
            item.setText(0, result.location());
            item.setText(1, String.valueOf(result.line()));
            item.setText(2, result.text() == null ? "" : result.text());
        }
        status.setText(results.size() + " match(es) for '" + query + "'");
    }

    public void setVisiblePanel(boolean visible) {
        if (parent.getVisible() == visible) {
            return;
        }
        parent.setVisible(visible);
        ((GridData) parent.getLayoutData()).exclude = !visible;
        parent.getParent().layout(true, true);
    }

    public boolean isVisiblePanel() {
        return parent.getVisible();
    }
}
