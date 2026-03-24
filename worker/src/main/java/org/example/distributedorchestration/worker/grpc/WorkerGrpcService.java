package org.example.distributedorchestration.worker.grpc;

import io.grpc.stub.StreamObserver;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.example.distributedorchestration.worker.domain.model.CompensateTaskCommand;
import org.example.distributedorchestration.worker.domain.model.ExecuteTaskCommand;
import org.example.distributedorchestration.worker.executor.DefaultTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC worker (Steps 8 and 11): maps transport requests to worker commands and executes them.
 */
@Component
public class WorkerGrpcService extends WorkerServiceGrpc.WorkerServiceImplBase {
    private static final Logger log = LoggerFactory.getLogger(WorkerGrpcService.class);

    @Override
    public void executeTask(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            log.debug("Worker execute request received taskId={}", request.getTaskId());
            ExecuteTaskCommand command = new ExecuteTaskCommand(
                    request.getTaskId(),
                    request.getPayload() == null ? "" : request.getPayload());
            DefaultTaskExecutor.forExecute(command).execute();
            log.info("Worker execute success taskId={}", request.getTaskId());
            responseObserver.onNext(successResponse());
        } catch (Exception e) {
            log.warn("Worker execute failure taskId={} message={}", request.getTaskId(), e.getMessage());
            responseObserver.onNext(failureResponse(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void compensateTask(CompensationRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            log.debug("Worker compensate request received workflowId={} taskId={}", request.getWorkflowId(), request.getTaskId());
            CompensateTaskCommand command = new CompensateTaskCommand(
                    request.getTaskId(),
                    request.getCompensationPayload() == null ? "" : request.getCompensationPayload());
            DefaultTaskExecutor.forCompensation(command).compensate();
            log.info("Worker compensate success workflowId={} taskId={}", request.getWorkflowId(), request.getTaskId());
            responseObserver.onNext(successResponse());
        } catch (Exception e) {
            log.warn(
                    "Worker compensate failure workflowId={} taskId={} message={}",
                    request.getWorkflowId(),
                    request.getTaskId(),
                    e.getMessage());
            responseObserver.onNext(failureResponse(e));
        }
        responseObserver.onCompleted();
    }

    private static TaskResponse successResponse() {
        return TaskResponse.newBuilder().setSuccess(true).setMessage("ok").build();
    }

    private static TaskResponse failureResponse(Exception e) {
        return TaskResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage() == null ? "error" : e.getMessage())
                .build();
    }
}
