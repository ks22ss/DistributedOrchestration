package io.github.ks22ss.distributedorchestration.worker.adapters.presentation.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/** Starts and stops the gRPC server for {@link WorkerGrpcService}. */
@Component
public class WorkerGrpcServerLifecycle implements SmartLifecycle {

    private final WorkerGrpcService workerGrpcService;
    private final int grpcPort;

    private Server server;
    private volatile boolean running;

    public WorkerGrpcServerLifecycle(
            WorkerGrpcService workerGrpcService,
            @Value("${orchestration.grpc.server.port:9090}") int grpcPort) {
        this.workerGrpcService = workerGrpcService;
        this.grpcPort = grpcPort;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        try {
            server =
                    NettyServerBuilder.forPort(grpcPort).addService(workerGrpcService).build().start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server on port " + grpcPort, e);
        }
        running = true;
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(10, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
            server = null;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}

