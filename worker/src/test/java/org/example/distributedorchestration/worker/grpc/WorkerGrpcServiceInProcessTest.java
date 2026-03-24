package org.example.distributedorchestration.worker.grpc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.example.distributedorchestration.common.worker.v1.CompensationRequest;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerGrpcServiceInProcessTest {

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        String name = "test-" + UUID.randomUUID();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new WorkerGrpcService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void executeAndCompensateRoundTrip() {
        WorkerServiceGrpc.WorkerServiceBlockingStub stub = WorkerServiceGrpc.newBlockingStub(channel);
        assertTrue(stub.executeTask(TaskRequest.newBuilder().setTaskId("t1").setPayload("p").build())
                .getSuccess());
        assertTrue(stub.compensateTask(CompensationRequest.newBuilder()
                        .setTaskId("t1")
                        .setWorkflowId("wf1")
                        .setCompensationPayload("undo")
                        .build())
                .getSuccess());
    }
}
