package org.example.distributedorchestration.orchestrator.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Spec Step 13 metrics: task execution time, retry counts, inputs for failure rate (success vs failure counters).
 */
@Component
public class OrchestrationMetrics {

    private final MeterRegistry registry;
    private final Counter dispatchRetryAttempts;
    private final Counter dispatchSuccess;
    private final Counter dispatchFailure;
    private final Counter compensationRetryAttempts;
    private final Counter compensationSuccess;
    private final Counter compensationFailure;

    public OrchestrationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.dispatchRetryAttempts = Counter.builder("orchestration.task.retry.attempts")
                .description("Dispatch retry attempts after a failed execute attempt (before terminal failure)")
                .register(registry);
        this.dispatchSuccess = Counter.builder("orchestration.task.dispatch.success")
                .description("Tasks that reached SUCCESS after dispatch")
                .register(registry);
        this.dispatchFailure = Counter.builder("orchestration.task.dispatch.failure")
                .description(
                        "Tasks that exhausted dispatch retries (terminal); pair with success for failure_rate in Prometheus")
                .register(registry);
        this.compensationRetryAttempts = Counter.builder("orchestration.compensation.retry.attempts")
                .description("Compensation RPC retry attempts")
                .register(registry);
        this.compensationSuccess = Counter.builder("orchestration.compensation.success")
                .description("Successful compensateTask completions")
                .register(registry);
        this.compensationFailure = Counter.builder("orchestration.compensation.failure")
                .description("Compensation exhausted retries or logical failure terminal")
                .register(registry);
    }

    /** Per executeTask RPC (spec: task_execution_time). */
    public void recordTaskExecutionTime(Duration duration, String outcome) {
        registry.timer("orchestration.task.execution", "outcome", outcome).record(duration);
    }

    public void recordDispatchRetryAttempt() {
        dispatchRetryAttempts.increment();
    }

    public void recordDispatchSuccess() {
        dispatchSuccess.increment();
    }

    public void recordDispatchTerminalFailure() {
        dispatchFailure.increment();
    }

    public void recordCompensationExecutionTime(Duration duration, String outcome) {
        registry.timer("orchestration.compensation.execution", "outcome", outcome).record(duration);
    }

    public void recordCompensationRetryAttempt() {
        compensationRetryAttempts.increment();
    }

    public void recordCompensationSuccess() {
        compensationSuccess.increment();
    }

    public void recordCompensationTerminalFailure() {
        compensationFailure.increment();
    }
}
