package org.example.distributedorchestration.orchestrator.infrastructure.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC clients to workers. Multiple comma-separated {@code orchestration.worker.endpoints} values build a
 * round-robin {@link WorkerStubPool}; when unset, {@code host} and {@code grpc-port} define a single target.
 */
@Configuration
public class OrchestratorGrpcClientConfiguration {

    @Bean
    public WorkerStubPool workerStubPool(
            @Value("${orchestration.worker.endpoints:}") String endpointsCsv,
            @Value("${orchestration.worker.host:localhost}") String host,
            @Value("${orchestration.worker.grpc-port:9090}") int port
    ) {
        List<HostPort> targets = parseTargets(endpointsCsv, host, port);
        List<ManagedChannel> channels = new ArrayList<>(targets.size());
        List<WorkerServiceGrpc.WorkerServiceBlockingStub> stubs = new ArrayList<>(targets.size());
        for (HostPort hp : targets) {
            ManagedChannel channel =
                    NettyChannelBuilder.forAddress(hp.host(), hp.port()).usePlaintext().build();
            channels.add(channel);
            stubs.add(WorkerServiceGrpc.newBlockingStub(channel));
        }
        return new WorkerStubPool(channels, stubs);
    }

    private static List<HostPort> parseTargets(String endpointsCsv, String defaultHost, int defaultPort) {
        if (endpointsCsv == null || endpointsCsv.isBlank()) {
            return List.of(new HostPort(defaultHost, defaultPort));
        }
        List<HostPort> out = new ArrayList<>();
        for (String raw : endpointsCsv.split(",")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            int colon = part.lastIndexOf(':');
            if (colon <= 0 || colon == part.length() - 1) {
                throw new IllegalArgumentException(
                        "Invalid worker endpoint (expected host:port): \"" + part + "\"");
            }
            String h = part.substring(0, colon);
            int p = Integer.parseInt(part.substring(colon + 1));
            out.add(new HostPort(h, p));
        }
        if (out.isEmpty()) {
            return List.of(new HostPort(defaultHost, defaultPort));
        }
        return List.copyOf(out);
    }

    private record HostPort(String host, int port) {}
}
