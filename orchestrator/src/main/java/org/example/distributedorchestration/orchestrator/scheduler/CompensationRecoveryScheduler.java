package org.example.distributedorchestration.orchestrator.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.example.distributedorchestration.orchestrator.application.service.WorkflowCompensationService;
import org.example.distributedorchestration.orchestrator.persistence.entity.WorkflowEntity;
import org.example.distributedorchestration.orchestrator.repository.WorkflowJpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompensationRecoveryScheduler {

    private final WorkflowJpaRepository workflowRepository;
    private final WorkflowCompensationService workflowCompensationService;

    @Scheduled(fixedDelayString = "${orchestration.compensation.recovery-interval-ms:60000}")
    public void recoverStuckCompensations() {
        for (WorkflowEntity wf : workflowRepository.findByStatus(WorkflowStatus.COMPENSATING)) {
            log.debug("Recovery: resuming compensation for workflowId={}", wf.getWorkflowId());
            workflowCompensationService.resumeStuckCompensation(wf.getWorkflowId());
        }
    }
}
