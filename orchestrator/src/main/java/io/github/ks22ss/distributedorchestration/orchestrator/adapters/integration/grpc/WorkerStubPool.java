package io.github.ks22ss.distributedorchestration.orchestrator.adapters.integration.grpc;

import io.grpc.ManagedChannel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.worker.v1.WorkerServiceGrpc;
import org.springframework.beans.factory.DisposableBean;

/** Round-robin pool of blocking worker stubs (one {@link ManagedChannel} per configured endpoint). */
@Slf4j
public class WorkerStubPool implements DisposableBean {

    private final List<ManagedChannel> channels;
    private final List<WorkerServiceGrpc.WorkerServiceBlockingStub> stubs;
    private final AtomicInteger roundRobin = new AtomicInteger();

    public WorkerStubPool(List<ManagedChannel> channels, List<WorkerServiceGrpc.WorkerServiceBlockingStub> stubs) {
        if (channels.isEmpty() || stubs.isEmpty() || channels.size() != stubs.size()) {
            throw new IllegalArgumentException("Channels and stubs must be non-empty and the same size");
        }
        this.channels = List.copyOf(channels);
        this.stubs = List.copyOf(stubs);
        log.info("Worker gRPC pool size={}", this.stubs.size());
    }

    public WorkerServiceGrpc.WorkerServiceBlockingStub nextStub() {
        int i = Math.floorMod(roundRobin.getAndIncrement(), stubs.size());
        return stubs.get(i);
    }

    @Override
    public void destroy() {
        for (ManagedChannel channel : channels) {
            channel.shutdown();
        }
    }
}

