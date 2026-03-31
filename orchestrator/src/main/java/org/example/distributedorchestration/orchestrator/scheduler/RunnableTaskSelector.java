package org.example.distributedorchestration.orchestrator.scheduler;

import java.time.Instant;
import java.util.List;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.Workflow;
import org.springframework.stereotype.Component;

/**
 * Selects tasks that are {@link TaskStatus#PENDING} and whose dependencies are all {@link TaskStatus#SUCCESS}.
 */
@Component
public class RunnableTaskSelector {

    /**
     * @param workflow in-memory snapshot of workflow and task states
     * @return runnable tasks in arbitrary order
     */
    public List<Task> findRunnableTasks(Workflow workflow) {
        List<Task> successful = workflow.successfulTasks();
        Instant now = Instant.now();
        return workflow.allTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .filter(t -> t.getNextRetryAt() == null || !t.getNextRetryAt().isAfter(now))
                .filter(t -> t.dependenciesSatisfiedBy(successful))
                .toList();
    }
}
