package org.example.distributedorchestration.worker.grpc;

import io.grpc.stub.StreamObserver;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.example.distributedorchestration.worker.executor.DefaultTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * gRPC worker (Steps 8 and 11): {@link WorkerServiceGrpc.WorkerServiceImplBase} with {@link org.example.distributedorchestration.common.execution.TaskExecutor}.
 */
@Component
public class WorkerGrpcService extends WorkerServiceGrpc.WorkerServiceImplBase {

    @Override
    public void executeTask(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            DefaultTaskExecutor.forExecute(request).execute();
            responseObserver.onNext(successResponse());
        } catch (Exception e) {
            responseObserver.onNext(failureResponse(e));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void compensateTask(CompensationRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            DefaultTaskExecutor.forCompensation(request).compensate();
            responseObserver.onNext(successResponse());
        } catch (Exception e) {
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
