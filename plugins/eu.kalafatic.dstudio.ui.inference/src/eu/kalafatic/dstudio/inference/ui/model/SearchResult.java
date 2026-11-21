package eu.kalafatic.dstudio.inference.ui.model;

/** One search hit rendered by the workspace search panel. */
public record SearchResult(String location, int line, String text) {
}
