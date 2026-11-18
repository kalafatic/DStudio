package eu.kalafatic.dstudio.core.model;

public enum ExecutionEventType {
    EXECUTION_QUEUED,
    EXECUTION_STARTED,
    PROGRESS_UPDATED,
    EXECUTION_LOG,
    TOOL_INVOKED,
    EXECUTION_COMPLETED,
    EXECUTION_FAILED,
    EXECUTION_CANCELLED
}
