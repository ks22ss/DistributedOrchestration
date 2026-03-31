package org.example.distributedorchestration.orchestrator.infrastructure.persistence.dispatch;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.orchestrator.application.event.TaskCompletedEvent;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.example.distributedorchestration.orchestrator.repository.TaskJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Persists dispatch outcomes in short transactions; backoff is deferred via {@code next_retry_at} (Step 9). */
@Service
@RequiredArgsConstructor
public class WorkerDispatchPersistence {

    private final TaskJpaRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${orchestration.dispatch.max-retries:5}")
    private int maxRetries;

    @Value("${orchestration.dispatch.in-flight-lease-seconds:120}")
    private int inFlightLeaseSeconds;

    /**
     * Claims a {@link TaskStatus#PENDING} task for dispatch: sets {@code next_retry_at} to a short lease so the
     * runnable selector and retry scanner skip it until success, failure rescheduling, or lease expiry (crash safety).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryBeginDispatch(TaskEntityId id, Instant now) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        if (entity.getStatus() != TaskStatus.PENDING) {
            return false;
        }
        if (entity.getNextRetryAt() != null && entity.getNextRetryAt().isAfter(now)) {
            return false;
        }
        entity.setNextRetryAt(now.plusSeconds(inFlightLeaseSeconds));
        taskRepository.save(entity);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(TaskEntityId id) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        entity.setStatus(TaskStatus.SUCCESS);
        entity.setRetryCount(0);
        entity.setNextRetryAt(null);
        taskRepository.save(entity);

        String workflowId = id.getWorkflowId();
        String taskId = id.getTaskId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(new TaskCompletedEvent(workflowId, taskId));
            }
        });
    }

    /**
     * Applies spec Step 9: {@code delay = (int) Math.pow(2, retryCount)} using the count <em>before</em> increment,
     * then increments {@code retry_count}. Marks {@link TaskStatus#FAILED} when retries are exhausted; otherwise
     * returns task to {@link TaskStatus#PENDING} and sets {@code next_retry_at}.
     *
     * @return delay until the next attempt (for logging), and whether dispatch should stop
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BackoffOutcome recordFailureAndScheduleRetry(TaskEntityId id, Instant now) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        int delaySeconds = (int) Math.pow(2, entity.getRetryCount());
        entity.setRetryCount(entity.getRetryCount() + 1);
        if (entity.getRetryCount() > maxRetries) {
            entity.setStatus(TaskStatus.FAILED);
            entity.setNextRetryAt(null);
            taskRepository.save(entity);
            return new BackoffOutcome(0, true);
        }
        entity.setStatus(TaskStatus.PENDING);
        entity.setNextRetryAt(now.plusSeconds(delaySeconds));
        taskRepository.save(entity);
        return new BackoffOutcome(delaySeconds, false);
    }

    public record BackoffOutcome(int delaySeconds, boolean exhausted) {}
}
