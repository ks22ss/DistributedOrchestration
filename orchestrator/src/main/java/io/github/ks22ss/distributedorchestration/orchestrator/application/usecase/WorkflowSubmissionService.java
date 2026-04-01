package io.github.ks22ss.distributedorchestration.orchestrator.application.usecase;

import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowTaskCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.event.WorkflowSubmittedEvent;
import io.github.ks22ss.distributedorchestration.orchestrator.application.result.SubmitWorkflowResult;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.DuplicateWorkflowException;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.InvalidWorkflowException;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.WorkflowJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.WorkflowDagValidator;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.SubmitWorkflowUseCase;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts workflow submissions: validates DAG, persists, then publishes an event to trigger execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowSubmissionService implements SubmitWorkflowUseCase {

    private final WorkflowDagValidator dagValidator;
    private final WorkflowJpaRepository workflowRepository;
    private final TaskJpaRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public SubmitWorkflowResult submit(SubmitWorkflowCommand command) {
        String workflowId = command.workflowId();
        log.debug("Start workflow submission workflowId={} taskCount={}", workflowId, command.tasks().size());
        if (workflowRepository.existsById(workflowId)) {
            throw new DuplicateWorkflowException("Workflow already exists: " + workflowId);
        }

        Map<String, Task> tasksById = buildDomainTasks(workflowId, command);
        dagValidator.validateOrThrow(tasksById);
        log.debug("Workflow validated workflowId={} uniqueTaskCount={}", workflowId, tasksById.size());

        Instant createdAt = Instant.now();
        workflowRepository.save(new WorkflowEntity(workflowId, WorkflowStatus.RUNNING, createdAt));

        for (SubmitWorkflowTaskCommand taskCmd : command.tasks()) {
            TaskEntityId id = new TaskEntityId(taskCmd.taskId(), workflowId);
            TaskEntity entity = new TaskEntity(
                    id,
                    TaskStatus.PENDING,
                    0,
                    taskCmd.payload(),
                    taskCmd.compensationPayload(),
                    taskCmd.dependencies() == null ? List.of() : taskCmd.dependencies());
            taskRepository.save(entity);
        }
        log.debug("Workflow persisted workflowId={} persistedTaskCount={}", workflowId, command.tasks().size());

        eventPublisher.publishEvent(new WorkflowSubmittedEvent(workflowId));
        log.info("Workflow submission completed workflowId={} status={}", workflowId, WorkflowStatus.RUNNING);
        return new SubmitWorkflowResult(workflowId, WorkflowStatus.RUNNING.name());
    }

    private static Map<String, Task> buildDomainTasks(String workflowId, SubmitWorkflowCommand command) {
        Map<String, Task> map = new LinkedHashMap<>();
        for (SubmitWorkflowTaskCommand taskCmd : command.tasks()) {
            String taskId = taskCmd.taskId();
            if (map.containsKey(taskId)) {
                throw new InvalidWorkflowException("Duplicate taskId in request: " + taskId);
            }
            map.put(
                    taskId,
                    Task.pending(
                            taskId,
                            workflowId,
                            taskCmd.dependencies() == null ? List.of() : taskCmd.dependencies(),
                            taskCmd.payload(),
                            taskCmd.compensationPayload()));
        }
        return map;
    }
}

