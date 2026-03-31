package org.example.distributedorchestration.orchestrator.scheduler;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.orchestrator.application.service.WorkflowExecutionService;
import org.example.distributedorchestration.orchestrator.repository.TaskJpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Picks up tasks whose exponential-backoff window has elapsed ({@code next_retry_at}) without blocking
 * request threads on {@link Thread#sleep}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DispatchRetryScheduler {

    private final TaskJpaRepository taskRepository;
    private final WorkflowExecutionService workflowExecutionService;

    @Scheduled(fixedDelayString = "${orchestration.dispatch.retry-scan-interval-ms:1000}")
    public void dispatchDueRetries() {
        Instant now = Instant.now();
        for (String workflowId : taskRepository.findDistinctWorkflowIdsWithRetriesDue(TaskStatus.PENDING, now)) {
            log.debug("Retry scan: triggering execution workflowId={}", workflowId);
            try {
                workflowExecutionService.triggerExecution(workflowId);
            } catch (RuntimeException e) {
                log.warn("Retry scan failed workflowId={}", workflowId, e);
            }
        }
    }
}
