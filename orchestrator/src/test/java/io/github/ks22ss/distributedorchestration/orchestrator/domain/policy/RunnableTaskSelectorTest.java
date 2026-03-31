package io.github.ks22ss.distributedorchestration.orchestrator.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.Workflow;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.junit.jupiter.api.Test;

class RunnableTaskSelectorTest {

    private final RunnableTaskSelector selector = new RunnableTaskSelector();

    @Test
    void findsRootsWhenNoSuccessfulTasks() {
        Map<String, Task> tasks =
                Map.of(
                        "A", Task.pending("A", "wf", List.of(), "p"),
                        "B", Task.pending("B", "wf", List.of("A"), "p"));
        Workflow wf = new Workflow("wf", tasks, WorkflowStatus.RUNNING);

        List<Task> runnable = selector.findRunnableTasks(wf);

        assertEquals(1, runnable.size());
        assertEquals("A", runnable.getFirst().getTaskId());
    }

    @Test
    void findsNextAfterDependenciesSucceeded() {
        Task a = Task.pending("A", "wf", List.of(), "p");
        a.setStatus(TaskStatus.SUCCESS);
        Task b = Task.pending("B", "wf", List.of("A"), "p");
        Map<String, Task> tasks = Map.of("A", a, "B", b);
        Workflow wf = new Workflow("wf", tasks, WorkflowStatus.RUNNING);

        List<Task> runnable = selector.findRunnableTasks(wf);

        assertEquals(1, runnable.size());
        assertEquals("B", runnable.getFirst().getTaskId());
    }

    @Test
    void nothingRunnableWhenEveryPendingTaskHasUnsatisfiedDeps() {
        Task a = Task.pending("A", "wf", List.of("B"), "p");
        Task b = Task.pending("B", "wf", List.of("A"), "p");
        Map<String, Task> tasks = Map.of("A", a, "B", b);
        Workflow wf = new Workflow("wf", tasks, WorkflowStatus.RUNNING);

        assertTrue(selector.findRunnableTasks(wf).isEmpty());
    }

    @Test
    void skipsPendingTasksWhoseNextRetryAtIsStillInTheFuture() {
        Task a =
                new Task(
                        "A",
                        "wf",
                        List.of(),
                        TaskStatus.PENDING,
                        0,
                        "p",
                        null,
                        Instant.now().plusSeconds(60));
        Map<String, Task> tasks = Map.of("A", a);
        Workflow wf = new Workflow("wf", tasks, WorkflowStatus.RUNNING);

        assertTrue(selector.findRunnableTasks(wf).isEmpty());
    }
}

