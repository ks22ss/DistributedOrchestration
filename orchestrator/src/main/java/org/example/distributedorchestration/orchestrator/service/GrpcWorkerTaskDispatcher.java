package org.example.distributedorchestration.orchestrator.service;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.stereotype.Component;

/**
 * Dispatches runnable tasks to workers via gRPC {@link WorkerServiceGrpc#executeTask}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GrpcWorkerTaskDispatcher implements WorkerTaskDispatcher {

    private final WorkerServiceGrpc.WorkerServiceBlockingStub workerStub;

    @Override
    public void dispatch(Task task) {
        TaskRequest request = TaskRequest.newBuilder()
                .setTaskId(task.getTaskId())
                .setPayload(task.getPayload() == null ? "" : task.getPayload())
                .build();
        try {
            var response = workerStub.executeTask(request);
            if (!response.getSuccess()) {
                log.warn(
                        "Worker reported failure workflowId={} taskId={} message={}",
                        task.getWorkflowId(),
                        task.getTaskId(),
                        response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            log.error(
                    "gRPC dispatch failed workflowId={} taskId={}",
                    task.getWorkflowId(),
                    task.getTaskId(),
                    e);
            throw e;
        }
    }
}
