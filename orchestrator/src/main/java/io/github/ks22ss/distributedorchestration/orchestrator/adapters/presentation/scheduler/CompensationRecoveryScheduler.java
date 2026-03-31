package io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.scheduler;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.WorkflowJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.WorkflowCompensationUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompensationRecoveryScheduler {

    private final WorkflowJpaRepository workflowRepository;
    private final WorkflowCompensationUseCase workflowCompensationService;

    @Scheduled(fixedDelayString = "${orchestration.compensation.recovery-interval-ms:60000}")
    public void recoverStuckCompensations() {
        for (WorkflowEntity wf : workflowRepository.findByStatus(WorkflowStatus.COMPENSATING)) {
            log.debug("Recovery: resuming compensation for workflowId={}", wf.getWorkflowId());
            workflowCompensationService.resumeStuckCompensation(wf.getWorkflowId());
        }
    }
}

