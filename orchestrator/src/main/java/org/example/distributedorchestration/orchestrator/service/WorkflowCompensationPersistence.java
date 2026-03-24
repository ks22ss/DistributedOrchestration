package org.example.distributedorchestration.orchestrator.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.example.distributedorchestration.orchestrator.persistence.entity.WorkflowEntity;
import org.example.distributedorchestration.orchestrator.repository.TaskJpaRepository;
import org.example.distributedorchestration.orchestrator.repository.WorkflowJpaRepository;
import org.example.distributedorchestration.orchestrator.saga.SagaCompensationOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowCompensationPersistence {

    private final TaskJpaRepository taskRepository;
    private final WorkflowJpaRepository workflowRepository;

    /**
     * CAS {@link WorkflowStatus#RUNNING} to {@link WorkflowStatus#COMPENSATING} (single winner). When
     * {@code recovery=true}, {@link WorkflowStatus#COMPENSATING} workflows load remaining SUCCESS tasks only
     * (scheduler). When {@code recovery=false} and status is already COMPENSATING, returns SKIP so duplicate
     * async triggers do not re-run the saga.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CompensationStartResult tryStartOrResumeCompensation(String workflowId, boolean recovery) {
        WorkflowEntity wf = workflowRepository.findById(workflowId).orElseThrow();
        if (wf.getStatus() == WorkflowStatus.FAILED
                || wf.getStatus() == WorkflowStatus.SUCCESS
                || wf.getStatus() == WorkflowStatus.COMPENSATED) {
            return CompensationStartResult.skip();
        }
        if (wf.getStatus() == WorkflowStatus.COMPENSATING) {
            if (!recovery) {
                return CompensationStartResult.skip();
            }
            return buildCompensationPlan(wf);
        }
        if (wf.getStatus() == WorkflowStatus.RUNNING) {
            int updated =
                    workflowRepository.tryTransitionStatus(workflowId, WorkflowStatus.RUNNING, WorkflowStatus.COMPENSATING);
            if (updated == 0) {
                return CompensationStartResult.skip();
            }
            wf = workflowRepository.findById(workflowId).orElseThrow();
            return buildCompensationPlan(wf);
        }
        return CompensationStartResult.skip();
    }

    private CompensationStartResult buildCompensationPlan(WorkflowEntity wf) {
        String workflowId = wf.getWorkflowId();
        List<TaskEntity> all = taskRepository.findByWorkflowId(workflowId);
        List<TaskEntity> success =
                all.stream().filter(t -> t.getStatus() == TaskStatus.SUCCESS).toList();
        if (success.isEmpty()) {
            wf.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(wf);
            return CompensationStartResult.noSuccessTasks();
        }
        List<TaskEntity> order = SagaCompensationOrder.reverseTopologicalSuccessOrder(success);
        List<CompensationItem> items = order.stream()
                .map(t -> new CompensationItem(
                        t.getId(),
                        t.getId().getWorkflowId(),
                        t.getCompensationPayload() == null ? "" : t.getCompensationPayload()))
                .toList();
        return CompensationStartResult.run(items);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensated(TaskEntityId id) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        if (entity.getStatus() == TaskStatus.COMPENSATED) {
            return;
        }
        if (entity.getStatus() != TaskStatus.SUCCESS) {
            return;
        }
        entity.setStatus(TaskStatus.COMPENSATED);
        taskRepository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompensationFailed(TaskEntityId id) {
        TaskEntity entity = taskRepository.findById(id).orElseThrow();
        if (entity.getStatus() != TaskStatus.SUCCESS) {
            return;
        }
        entity.setStatus(TaskStatus.COMPENSATION_FAILED);
        taskRepository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishWorkflowAfterCompensation(String workflowId) {
        WorkflowEntity wf = workflowRepository.findById(workflowId).orElseThrow();
        if (wf.getStatus() == WorkflowStatus.COMPENSATING) {
            wf.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(wf);
        }
    }
}
