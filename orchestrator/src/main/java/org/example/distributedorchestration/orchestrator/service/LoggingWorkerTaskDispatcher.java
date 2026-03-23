package org.example.distributedorchestration.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.springframework.stereotype.Component;

/**
 * Placeholder dispatcher until gRPC worker execution is wired.
 */
@Component
@Slf4j
public class LoggingWorkerTaskDispatcher implements WorkerTaskDispatcher {

    @Override
    public void dispatch(Task task) {
        log.info(
                "Dispatch runnable task workflowId={} taskId={} payloadLength={}",
                task.getWorkflowId(),
                task.getTaskId(),
                task.getPayload() == null ? 0 : task.getPayload().length()
        );
    }
}
