package eu.kalafatic.dstudio.inference.ui.model;

import java.util.List;
import java.util.Objects;

/**
 * Declarative description of one configuration selector (toolbar combo). The
 * presentation renders the selector; the product supplies options from its real
 * registries and reacts to selection changes.
 */
public record SelectorSpec(String id, String label, List<SelectOption> options,
                           String selectedId, String description,
                           boolean prominent) {

    public SelectorSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        options = options == null ? List.of() : List.copyOf(options);
    }

    public SelectorSpec(String id, String label, List<SelectOption> options,
                        String selectedId, String description) {
        this(id, label, options, selectedId, description, false);
    }
}
