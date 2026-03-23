package org.example.distributedorchestration.orchestrator.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowRequest;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowResponse;
import org.example.distributedorchestration.orchestrator.api.dto.SubmitWorkflowTaskDto;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.example.distributedorchestration.orchestrator.persistence.entity.WorkflowEntity;
import org.example.distributedorchestration.orchestrator.repository.TaskJpaRepository;
import org.example.distributedorchestration.orchestrator.repository.WorkflowJpaRepository;
import org.example.distributedorchestration.orchestrator.scheduler.InvalidWorkflowException;
import org.example.distributedorchestration.orchestrator.scheduler.WorkflowDagValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts workflow submissions: validates DAG, persists, then publishes an event to trigger execution.
 */
@Service
@RequiredArgsConstructor
public class WorkflowSubmissionService {

    private final WorkflowDagValidator dagValidator;
    private final WorkflowJpaRepository workflowRepository;
    private final TaskJpaRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SubmitWorkflowResponse submit(SubmitWorkflowRequest request) {
        String workflowId = request.workflowId();
        if (workflowRepository.existsById(workflowId)) {
            throw new DuplicateWorkflowException("Workflow already exists: " + workflowId);
        }

        Map<String, Task> tasksById = buildDomainTasks(workflowId, request);
        dagValidator.validateOrThrow(tasksById);

        Instant createdAt = Instant.now();
        workflowRepository.save(new WorkflowEntity(workflowId, WorkflowStatus.RUNNING, createdAt));

        for (SubmitWorkflowTaskDto taskDto : request.tasks()) {
            TaskEntityId id = new TaskEntityId(taskDto.taskId(), workflowId);
            TaskEntity entity = new TaskEntity(
                    id,
                    TaskStatus.PENDING,
                    0,
                    taskDto.payload(),
                    taskDto.dependencies() == null ? List.of() : taskDto.dependencies());
            taskRepository.save(entity);
        }

        eventPublisher.publishEvent(new WorkflowSubmittedEvent(workflowId));
        return new SubmitWorkflowResponse(workflowId, WorkflowStatus.RUNNING.name());
    }

    private static Map<String, Task> buildDomainTasks(String workflowId, SubmitWorkflowRequest request) {
        Map<String, Task> map = new LinkedHashMap<>();
        for (SubmitWorkflowTaskDto dto : request.tasks()) {
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
                            dto.payload()));
        }
        return map;
    }
}
