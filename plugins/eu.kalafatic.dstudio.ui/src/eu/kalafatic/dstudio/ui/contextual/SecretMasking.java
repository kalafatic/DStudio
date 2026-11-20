package eu.kalafatic.dstudio.ui.contextual;

/**
 * Shared detection of masked/empty secret values in configuration dialogs.
 *
 * <p>Adapter read-back masks secrets as {@code "****** (configured)"} (harness and
 * inference-engine adapters) and the contextual-action framework masks them as
 * {@code "••••••••"}. A configuration dialog must never write such a marker (or a
 * blank value) back to the registry: the key is omitted from the update map so the
 * previously stored value survives.</p>
 */
public final class SecretMasking {

    /** Mask used by the contextual-action framework (harness/provider config views). */
    public static final String MASK_BULLETS = "••••••••";

    /** Mask produced by harness and inference-engine adapter read-backs. */
    public static final String MASK_CONFIGURED = "****** (configured)";

    private SecretMasking() {
    }

    /**
     * True when the value must not be written back: a known mask marker or null/blank.
     */
    public static boolean isMasked(String value) {
        return value == null || value.isBlank()
                || MASK_BULLETS.equals(value)
                || MASK_CONFIGURED.equals(value);
    }
}
