package eu.kalafatic.dstudio.inference.ui;

import eu.kalafatic.dstudio.inference.ui.model.SelectOption;
import eu.kalafatic.dstudio.inference.ui.model.SelectorSpec;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the configuration toolbar from a declarative selector spec. Each selector is
 * a labeled read-only combo; the product reacts to selection changes and can update
 * options, visibility and enablement dynamically (capability-aware configuration).
 */
public final class ToolbarBuilder {

    /** Notified on user-initiated selection changes (option id or null). */
    public interface Listener {
        void selectorChanged(String selectorId, String optionId);
    }

    private static final class Selector {
        final SelectorSpec spec;
        final Label label;
        final Combo combo;
        boolean visible = true;
        boolean loading;

        Selector(SelectorSpec spec, Label label, Combo combo) {
            this.spec = spec;
            this.label = label;
            this.combo = combo;
        }
    }

    private final Composite parent;
    private final Listener listener;
    private final Map<String, Selector> selectors = new LinkedHashMap<>();

    public ToolbarBuilder(Composite parent, Listener listener) {
        this.parent = parent;
        this.listener = listener;
    }

    /** Rebuilds the toolbar from the spec list. */
    public void setSelectors(List<SelectorSpec> specs) {
        for (Selector selector : selectors.values()) {
            selector.label.dispose();
            selector.combo.dispose();
        }
        selectors.clear();
        for (SelectorSpec spec : specs) {
            Label label = new Label(parent, SWT.NONE);
            label.setText(spec.label() + ":");
            Combo combo = new Combo(parent, SWT.READ_ONLY | SWT.BORDER);
            combo.setToolTipText(spec.description());
            GridData labelData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
            GridData comboData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
            comboData.widthHint = comboWidth(spec);
            label.setLayoutData(labelData);
            combo.setLayoutData(comboData);
            Selector selector = new Selector(spec, label, combo);
            selectors.put(spec.id(), selector);
            applyOptions(selector, spec.options(), spec.selectedId());
            combo.addModifyListener(event -> {
                if (selector.loading) {
                    return;
                }
                int index = combo.getSelectionIndex();
                String optionId = index >= 0 ? (String) combo.getData(combo.getItem(index))
                        : null;
                listener.selectorChanged(spec.id(), optionId);
            });
        }
        relayout();
    }

    private static int comboWidth(SelectorSpec spec) {
        return spec.prominent() ? 170 : switch (spec.id()) {
            case "project" -> 150;
            case "provider", "harness" -> 140;
            case "model" -> 200;
            case "agent", "profile" -> 130;
            default -> 120;
        };
    }

    /** Updates one selector's options and selection. */
    public void updateSelector(String selectorId, List<SelectOption> options,
                               String selectedId) {
        Selector selector = selectors.get(selectorId);
        if (selector == null || options == null) {
            return;
        }
        applyOptions(selector, options, selectedId);
        relayout();
    }

    public void setSelectorVisible(String selectorId, boolean visible) {
        Selector selector = selectors.get(selectorId);
        if (selector == null || selector.visible == visible) {
            return;
        }
        selector.visible = visible;
        selector.label.setVisible(visible);
        selector.combo.setVisible(visible);
        relayout();
    }

    public void setSelectorEnabled(String selectorId, boolean enabled) {
        Selector selector = selectors.get(selectorId);
        if (selector != null) {
            selector.combo.setEnabled(enabled);
        }
    }

    /** The selected option id, or null. */
    public String selectedOption(String selectorId) {
        Selector selector = selectors.get(selectorId);
        if (selector == null) {
            return null;
        }
        int index = selector.combo.getSelectionIndex();
        if (index < 0) {
            return null;
        }
        return (String) selector.combo.getData(selector.combo.getItem(index));
    }

    public boolean isVisible(String selectorId) {
        Selector selector = selectors.get(selectorId);
        return selector != null && selector.visible;
    }

    private void applyOptions(Selector selector, List<SelectOption> options,
                              String selectedId) {
        selector.loading = true;
        try {
            selector.combo.removeAll();
            int selectIndex = -1;
            int recommendedIndex = 0;
            for (SelectOption option : options) {
                String label = option.label() == null || option.label().isBlank()
                        ? option.id() : option.label();
                if (option.recommended() && !label.endsWith(" *")) {
                    label = label + " \u25CF";
                }
                selector.combo.add(label);
                selector.combo.setData(label, option.id());
                if (option.id() != null && option.id().equals(selectedId)) {
                    selectIndex = selector.combo.getItemCount() - 1;
                }
                if (option.recommended()) {
                    recommendedIndex = selector.combo.getItemCount() - 1;
                }
            }
            if (selectIndex < 0 && selectedId != null && !selectedId.isBlank()
                    && !options.isEmpty()) {
                // Unknown selected id: keep the first option but keep the requested id
                // if the product already stores it (options may be stale).
                selectIndex = 0;
            }
            if (selectIndex >= 0) {
                selector.combo.select(selectIndex);
            } else if (selector.combo.getItemCount() > 0) {
                selector.combo.select(recommendedIndex);
            }
            selector.combo.setEnabled(!options.isEmpty());
        } finally {
            selector.loading = false;
        }
    }

    private void relayout() {
        int visibleCount = 0;
        for (Selector selector : selectors.values()) {
            if (selector.visible) {
                visibleCount++;
            }
        }
        GridLayout layout = (GridLayout) parent.getLayout();
        int targetColumns = Math.max(2, visibleCount * 2);
        if (layout.numColumns != targetColumns) {
            layout.numColumns = targetColumns;
        }
        parent.layout(true, true);
    }
}
