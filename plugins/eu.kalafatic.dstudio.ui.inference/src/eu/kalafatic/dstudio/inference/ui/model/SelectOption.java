package eu.kalafatic.dstudio.inference.ui.model;

/**
 * One selectable option of a configuration selector. Options come from the product's
 * real registries; {@code recommended} marks the backend default.
 */
public record SelectOption(String id, String label, String description,
                           boolean recommended) {

    public SelectOption(String id, String label) {
        this(id, label, "", false);
    }

    public SelectOption(String id, String label, String description) {
        this(id, label, description, false);
    }

    public SelectOption withRecommended(boolean value) {
        return new SelectOption(id, label, description, value);
    }
}
