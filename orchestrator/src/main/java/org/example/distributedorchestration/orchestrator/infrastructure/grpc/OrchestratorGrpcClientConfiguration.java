package org.example.distributedorchestration.orchestrator.infrastructure.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC client to workers (spec Step 7).
 */
@Configuration
public class OrchestratorGrpcClientConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel workerManagedChannel(
            @Value("${orchestration.worker.host:localhost}") String host,
            @Value("${orchestration.worker.grpc-port:9090}") int port) {
        return NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
    }

    @Bean
    public WorkerServiceGrpc.WorkerServiceBlockingStub workerServiceBlockingStub(ManagedChannel workerManagedChannel) {
        return WorkerServiceGrpc.newBlockingStub(workerManagedChannel);
    }
}
