package org.example.distributedorchestration.orchestrator.application.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowRequest;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowResponse;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowTask;
import org.example.distributedorchestration.orchestrator.application.event.WorkflowSubmittedEvent;
import org.example.distributedorchestration.orchestrator.domain.exception.DuplicateWorkflowException;
import org.example.distributedorchestration.orchestrator.domain.exception.InvalidWorkflowException;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.example.distributedorchestration.orchestrator.persistence.entity.WorkflowEntity;
import org.example.distributedorchestration.orchestrator.repository.TaskJpaRepository;
import org.example.distributedorchestration.orchestrator.repository.WorkflowJpaRepository;
import org.example.distributedorchestration.orchestrator.scheduler.WorkflowDagValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts workflow submissions: validates DAG, persists, then publishes an event to trigger execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowSubmissionService {

    private final WorkflowDagValidator dagValidator;
    private final WorkflowJpaRepository workflowRepository;
    private final TaskJpaRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SubmitWorkflowResponse submit(SubmitWorkflowRequest request) {
        String workflowId = request.workflowId();
        log.debug("Start workflow submission workflowId={} taskCount={}", workflowId, request.tasks().size());
        if (workflowRepository.existsById(workflowId)) {
            throw new DuplicateWorkflowException("Workflow already exists: " + workflowId);
        }

        Map<String, Task> tasksById = buildDomainTasks(workflowId, request);
        dagValidator.validateOrThrow(tasksById);
        log.debug("Workflow validated workflowId={} uniqueTaskCount={}", workflowId, tasksById.size());

        Instant createdAt = Instant.now();
        workflowRepository.save(new WorkflowEntity(workflowId, WorkflowStatus.RUNNING, createdAt));

        for (SubmitWorkflowTask taskDto : request.tasks()) {
            TaskEntityId id = new TaskEntityId(taskDto.taskId(), workflowId);
            TaskEntity entity = new TaskEntity(
                    id,
                    TaskStatus.PENDING,
                    0,
                    taskDto.payload(),
                    taskDto.compensationPayload(),
                    taskDto.dependencies() == null ? List.of() : taskDto.dependencies());
            taskRepository.save(entity);
        }
        log.debug("Workflow persisted workflowId={} persistedTaskCount={}", workflowId, request.tasks().size());

        eventPublisher.publishEvent(new WorkflowSubmittedEvent(workflowId));
        log.info("Workflow submission completed workflowId={} status={}", workflowId, WorkflowStatus.RUNNING);
        return new SubmitWorkflowResponse(workflowId, WorkflowStatus.RUNNING.name());
    }

    private static Map<String, Task> buildDomainTasks(String workflowId, SubmitWorkflowRequest request) {
        Map<String, Task> map = new LinkedHashMap<>();
        for (SubmitWorkflowTask dto : request.tasks()) {
            String taskId = dto.taskId();
            if (map.containsKey(taskId)) {
                throw new InvalidWorkflowException("Duplicate taskId in request: " + taskId);
            }
            map.put(
                    taskId,
                    Task.pending(
                            taskId,
                            workflowId,
                            dto.dependencies() == null ? List.of() : dto.dependencies(),
                            dto.payload(),
                            dto.compensationPayload()));
        }
        return map;
    }
}
