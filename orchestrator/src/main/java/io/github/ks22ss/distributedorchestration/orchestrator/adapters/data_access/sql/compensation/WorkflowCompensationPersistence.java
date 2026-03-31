package io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.compensation;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.WorkflowJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.entities.CompensationItem;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.entities.CompensationStartResult;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.SagaCompensationOrder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkflowCompensationPersistence {

    private final TaskJpaRepository taskRepository;
    private final WorkflowJpaRepository workflowRepository;

    /**
     * CAS {@link WorkflowStatus#RUNNING} to {@link WorkflowStatus#COMPENSATING} (single winner). When {@code
     * recovery=true}, {@link WorkflowStatus#COMPENSATING} workflows load remaining SUCCESS tasks only (scheduler). When
     * {@code recovery=false} and status is already COMPENSATING, returns SKIP so duplicate async triggers do not re-run
     * the saga.
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
        List<TaskEntity> success = all.stream().filter(t -> t.getStatus() == TaskStatus.SUCCESS).toList();
        if (success.isEmpty()) {
            wf.setStatus(WorkflowStatus.FAILED);
            workflowRepository.save(wf);
            return CompensationStartResult.noSuccessTasks();
        }
        var byId = success.stream().collect(java.util.stream.Collectors.toMap(t -> t.getId().getTaskId(), t -> t));
        var deps = new java.util.HashMap<String, java.util.List<String>>();
        for (TaskEntity t : success) {
            java.util.List<String> filtered =
                    t.getDependencies().stream().filter(byId::containsKey).toList();
            deps.put(t.getId().getTaskId(), filtered);
        }
        java.util.List<String> orderedIds = SagaCompensationOrder.reverseTopologicalSuccessOrder(deps);
        List<CompensationItem> items =
                orderedIds.stream()
                        .map(byId::get)
                        .map(
                                t ->
                                        new CompensationItem(
                                                t.getId(),
                                                t.getId().getWorkflowId(),
                                                t.getCompensationPayload() == null
                                                        ? ""
                                                        : t.getCompensationPayload()))
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

