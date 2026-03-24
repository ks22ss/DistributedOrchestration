package org.example.distributedorchestration.worker.grpc;

import io.grpc.stub.StreamObserver;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.stereotype.Component;

/**
 * gRPC worker: executes {@link WorkerServiceGrpc#executeTask} (spec Step 7/8).
 */
@Component
public class WorkerGrpcService extends WorkerServiceGrpc.WorkerServiceImplBase {

    @Override
    public void executeTask(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        try {
            // Placeholder execution: Step 8+ can plug real executors / payload handlers.
            responseObserver.onNext(
                    TaskResponse.newBuilder().setSuccess(true).setMessage("accepted").build());
        } catch (Exception e) {
            responseObserver.onNext(TaskResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage() == null ? "error" : e.getMessage())
                    .build());
        }
        responseObserver.onCompleted();
    }
}
