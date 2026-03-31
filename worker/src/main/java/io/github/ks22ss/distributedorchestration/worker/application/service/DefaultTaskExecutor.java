package io.github.ks22ss.distributedorchestration.worker.application.service;

import io.github.ks22ss.distributedorchestration.worker.application.command.CompensateTaskCommand;
import io.github.ks22ss.distributedorchestration.worker.application.command.ExecuteTaskCommand;
import io.github.ks22ss.distributedorchestration.worker.application.port.in.TaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default {@link TaskExecutor}: idempotent {@link #compensate()} when payload is empty or repeated RPCs. */
public final class DefaultTaskExecutor implements TaskExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultTaskExecutor.class);

    private final String taskId;
    private final String forwardPayload;
    private final String compensationPayload;

    public static DefaultTaskExecutor forExecute(ExecuteTaskCommand command) {
        return new DefaultTaskExecutor(
                command.taskId(),
                command.payload() == null ? "" : command.payload(),
                "");
    }

    public static DefaultTaskExecutor forCompensation(CompensateTaskCommand command) {
        return new DefaultTaskExecutor(
                command.taskId(),
                "",
                command.compensationPayload() == null ? "" : command.compensationPayload());
    }

    private DefaultTaskExecutor(String taskId, String forwardPayload, String compensationPayload) {
        this.taskId = taskId;
        this.forwardPayload = forwardPayload;
        this.compensationPayload = compensationPayload;
    }

    @Override
    public void execute() {
        log.debug(
                "Default executor execute start taskId={} payloadLength={}",
                taskId,
                forwardPayload.length());
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("task_id is blank");
        }
        log.debug("Default executor execute completed taskId={}", taskId);
    }

    @Override
    public void compensate() {
        log.debug(
                "Default executor compensate start taskId={} payloadLength={}",
                taskId,
                compensationPayload.length());
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("task_id is blank");
        }
        if (compensationPayload.isEmpty()) {
            log.debug("Default executor compensate skipped taskId={} reason=empty_payload", taskId);
            return;
        }
        log.debug("Default executor compensate completed taskId={}", taskId);
    }
}

