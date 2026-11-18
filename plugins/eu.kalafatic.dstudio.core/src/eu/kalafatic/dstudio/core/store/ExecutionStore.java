package eu.kalafatic.dstudio.core.store;

import eu.kalafatic.dstudio.core.model.ExecutionEvent;
import eu.kalafatic.dstudio.core.model.ExecutionTask;
import java.util.List;
import java.util.Optional;

public interface ExecutionStore {

    void saveExecution(ExecutionTask task);

    void updateExecution(ExecutionTask task);

    Optional<ExecutionTask> findExecution(String executionId);

    Optional<ExecutionTask> findExecutionByIdempotencyKey(String idempotencyKey);

    void saveIdempotency(String idempotencyKey, String executionId);

    List<ExecutionTask> listOpenExecutions();

    List<ExecutionTask> listExecutions();

    void appendEvent(ExecutionEvent event);

    List<ExecutionEvent> loadEvents(String executionId, long afterSequence, int limit);

    long nextSequence(String executionId);

    void clearAll();
}
