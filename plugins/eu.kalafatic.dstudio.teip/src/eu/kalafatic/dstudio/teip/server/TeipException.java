package eu.kalafatic.dstudio.teip.server;

public class TeipException extends RuntimeException {

    private final TeipWire.ErrorCode code;
    private final java.util.Map<String, Object> details;

    public TeipException(TeipWire.ErrorCode code, String message,
            java.util.Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? java.util.Map.of() : details;
    }

    public TeipWire.ErrorCode code() {
        return code;
    }

    public java.util.Map<String, Object> details() {
        return details;
    }
}
