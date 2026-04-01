package io.github.ks22ss.distributedorchestration.orchestrator.application.usecase;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.WorkflowJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.out.WorkerTaskDispatcher;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.RunnableTaskSelector;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.WorkflowExecutionUseCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.Workflow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads persisted state and dispatches runnable tasks to workers over gRPC. */
@Service
@Slf4j
public class WorkflowExecutionService implements WorkflowExecutionUseCase {

    private final WorkflowJpaRepository workflowRepository;
    private final TaskJpaRepository taskRepository;
    private final RunnableTaskSelector runnableTaskSelector;
    private final WorkerTaskDispatcher workerTaskDispatcher;
    private final Executor dispatchExecutor;

    public WorkflowExecutionService(
            WorkflowJpaRepository workflowRepository,
            TaskJpaRepository taskRepository,
            RunnableTaskSelector runnableTaskSelector,
            WorkerTaskDispatcher workerTaskDispatcher,
            @Qualifier("dispatchExecutor") Executor dispatchExecutor) {
        this.workflowRepository = workflowRepository;
        this.taskRepository = taskRepository;
        this.runnableTaskSelector = runnableTaskSelector;
        this.workerTaskDispatcher = workerTaskDispatcher;
        this.dispatchExecutor = dispatchExecutor;
    }

    /**
     * Runs after submit transaction commits; finds runnable tasks and dispatches them.
     *
     * @param workflowId persisted workflow id
     */
    @Transactional(readOnly = true)
    @Override
    public void triggerExecution(String workflowId) {
        log.info("Trigger execution start workflowId={}", workflowId);
        WorkflowEntity workflowEntity =
                workflowRepository
                        .findById(workflowId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Workflow not found after submit: " + workflowId));
        List<TaskEntity> entities = taskRepository.findByWorkflowId(workflowId);
        Map<String, Task> domainTasks = new LinkedHashMap<>();
        for (TaskEntity entity : entities) {
            domainTasks.put(entity.getId().getTaskId(), toDomainTask(entity));
        }
        Workflow workflow = new Workflow(workflowId, domainTasks, workflowEntity.getStatus());
        List<Task> runnable = runnableTaskSelector.findRunnableTasks(workflow);
        log.info(
                "Runnable tasks resolved workflowId={} runnableCount={} totalTasks={}",
                workflowId,
                runnable.size(),
                domainTasks.size());
        runnable.forEach(
                task -> {
                    log.info(
                            "Submitting dispatch workflowId={} taskId={}",
                            task.getWorkflowId(),
                            task.getTaskId());
                    dispatchExecutor.execute(
                            () -> {
                                try {
                                    workerTaskDispatcher.dispatch(task);
                                } catch (RuntimeException e) {
                                    log.error(
                                            "Uncaught dispatch failure workflowId={} taskId={}",
                                            task.getWorkflowId(),
                                            task.getTaskId(),
                                            e);
                                }
                            });
                });
    }

    private static Task toDomainTask(TaskEntity entity) {
        return new Task(
                entity.getId().getTaskId(),
                entity.getId().getWorkflowId(),
                entity.getDependencies(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getPayload(),
                entity.getCompensationPayload(),
                entity.getNextRetryAt());
    }
}

