package org.example.distributedorchestration.orchestrator.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.Workflow;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.WorkflowEntity;
import org.example.distributedorchestration.orchestrator.repository.TaskJpaRepository;
import org.example.distributedorchestration.orchestrator.repository.WorkflowJpaRepository;
import org.example.distributedorchestration.orchestrator.scheduler.RunnableTaskSelector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads persisted state and dispatches runnable tasks to workers over gRPC. */
@Service
@RequiredArgsConstructor
public class WorkflowExecutionService {

    private final WorkflowJpaRepository workflowRepository;
    private final TaskJpaRepository taskRepository;
    private final RunnableTaskSelector runnableTaskSelector;
    private final WorkerTaskDispatcher workerTaskDispatcher;

    /**
     * Runs after submit transaction commits; finds runnable tasks and dispatches them.
     *
     * @param workflowId persisted workflow id
     */
    @Transactional(readOnly = true)
    public void triggerExecution(String workflowId) {
        WorkflowEntity workflowEntity =
                workflowRepository.findById(workflowId).orElseThrow(() -> new IllegalStateException(
                        "Workflow not found after submit: " + workflowId));
        List<TaskEntity> entities = taskRepository.findByWorkflowId(workflowId);
        Map<String, Task> domainTasks = new LinkedHashMap<>();
        for (TaskEntity entity : entities) {
            domainTasks.put(entity.getId().getTaskId(), toDomainTask(entity));
        }
        Workflow workflow = new Workflow(workflowId, domainTasks, workflowEntity.getStatus());
        List<Task> runnable = runnableTaskSelector.findRunnableTasks(workflow);
        for (Task task : runnable) {
            workerTaskDispatcher.dispatch(task);
        }
    }

    private static Task toDomainTask(TaskEntity entity) {
        return new Task(
                entity.getId().getTaskId(),
                entity.getId().getWorkflowId(),
                entity.getDependencies(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getPayload()
        );
    }
}
