package eu.kalafatic.dstudio.core.inference;

/**
 * Semantic failure of an inference operation. Codes map to HTTP statuses at the API
 * boundary (NOT_FOUND→404, CONFLICT→409, VALIDATION_FAILED→400, INFERENCE_FAILED→502)
 * and stay transport-neutral for RCP consumers.
 */
public class InferenceException extends RuntimeException {

    public enum Code {
        NOT_FOUND, CONFLICT, VALIDATION_FAILED, INFERENCE_FAILED
    }

    private final Code code;

    public InferenceException(Code code, String message) {
        super(message == null ? "" : message);
        this.code = code;
    }

    public InferenceException(Code code, String message, Throwable cause) {
        super(message == null ? "" : message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
