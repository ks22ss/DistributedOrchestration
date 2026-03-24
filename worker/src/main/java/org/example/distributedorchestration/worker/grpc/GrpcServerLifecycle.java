package org.example.distributedorchestration.worker.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Starts and stops the gRPC {@link Server} for worker RPCs.
 */
@Component
public class GrpcServerLifecycle {

    private final WorkerGrpcService workerGrpcService;

    @Value("${orchestration.grpc.server.port:9090}")
    private int port;

    private Server server;

    public GrpcServerLifecycle(WorkerGrpcService workerGrpcService) {
        this.workerGrpcService = workerGrpcService;
    }

    @PostConstruct
    public void start() throws IOException {
        server = NettyServerBuilder.forPort(port)
                .addService(workerGrpcService)
                .build()
                .start();
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
