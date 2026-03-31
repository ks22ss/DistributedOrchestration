package io.github.ks22ss.distributedorchestration.orchestrator.domain.policy;

import java.time.Instant;
import java.util.List;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.Workflow;

/** Selects tasks that are PENDING, eligible by {@code next_retry_at}, and whose dependencies are all SUCCESS. */
public class RunnableTaskSelector {

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

