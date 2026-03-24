package org.example.distributedorchestration.worker.executor;

import org.example.distributedorchestration.common.execution.TaskExecutor;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;

/**
 * Default {@link TaskExecutor}: idempotent {@link #compensate()} when payload is empty or repeated RPCs.
 */
public final class DefaultTaskExecutor implements TaskExecutor {

    private final String taskId;
    private final String forwardPayload;
    private final String compensationPayload;

    public static DefaultTaskExecutor forExecute(TaskRequest request) {
        return new DefaultTaskExecutor(
                request.getTaskId(),
                request.getPayload() == null ? "" : request.getPayload(),
                "");
    }

    public static DefaultTaskExecutor forCompensation(CompensationRequest request) {
        return new DefaultTaskExecutor(
                request.getTaskId(),
                "",
                request.getCompensationPayload() == null ? "" : request.getCompensationPayload());
    }

    private DefaultTaskExecutor(String taskId, String forwardPayload, String compensationPayload) {
        this.taskId = taskId;
        this.forwardPayload = forwardPayload;
        this.compensationPayload = compensationPayload;
    }

    @Override
    public void execute() {
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("task_id is blank");
        }
    }

    @Override
    public void compensate() {
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("task_id is blank");
        }
        if (compensationPayload.isEmpty()) {
            return;
        }
    }
}
