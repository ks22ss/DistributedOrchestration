package org.example.distributedorchestration.worker.grpc;

import io.grpc.stub.StreamObserver;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.stereotype.Component;

/**
 * gRPC worker implementation (spec Step 8): {@link WorkerServiceGrpc.WorkerServiceImplBase#executeTask}.
 */
@Component
public class WorkerGrpcService extends WorkerServiceGrpc.WorkerServiceImplBase {

    @Override
    public void executeTask(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            runTask(request);
            responseObserver.onNext(successResponse());
        } catch (Exception e) {
            responseObserver.onNext(failureResponse(e));
        }
        responseObserver.onCompleted();
    }

    /** Placeholder for real task execution (payload routing, idempotency, etc.). */
    private static void runTask(TaskRequest request) {
        if (request.getTaskId().isBlank()) {
            throw new IllegalArgumentException("task_id is blank");
        }
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
