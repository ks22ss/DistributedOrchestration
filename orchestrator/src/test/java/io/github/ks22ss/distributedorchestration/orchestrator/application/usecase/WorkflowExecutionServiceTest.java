package io.github.ks22ss.distributedorchestration.orchestrator.application.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.WorkflowJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.out.WorkerTaskDispatcher;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.RunnableTaskSelector;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkflowExecutionServiceTest {

    private final WorkflowJpaRepository workflowRepository = Mockito.mock(WorkflowJpaRepository.class);
    private final TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
    private final RunnableTaskSelector runnableTaskSelector = Mockito.mock(RunnableTaskSelector.class);
    private final WorkerTaskDispatcher workerTaskDispatcher = Mockito.mock(WorkerTaskDispatcher.class);
    private final Executor directExecutor = Runnable::run;

    private final WorkflowExecutionService service =
            new WorkflowExecutionService(
                    workflowRepository, taskRepository, runnableTaskSelector, workerTaskDispatcher, directExecutor);

    @Test
    void triggerExecutionThrowsWhenWorkflowMissing() {
        when(workflowRepository.findById("wf-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.triggerExecution("wf-1"));
        verify(workerTaskDispatcher, never()).dispatch(any());
    }

    @Test
    void triggerExecutionDoesNothingWhenNoRunnableTasks() {
        when(workflowRepository.findById("wf-1"))
                .thenReturn(Optional.of(new WorkflowEntity("wf-1", WorkflowStatus.RUNNING, Instant.EPOCH)));
        when(taskRepository.findByWorkflowId("wf-1")).thenReturn(List.of());
        when(runnableTaskSelector.findRunnableTasks(any())).thenReturn(List.of());

        service.triggerExecution("wf-1");

        verify(workerTaskDispatcher, never()).dispatch(any());
    }

    @Test
    void triggerExecutionDispatchesEachRunnableTask() {
        when(workflowRepository.findById("wf-1"))
                .thenReturn(Optional.of(new WorkflowEntity("wf-1", WorkflowStatus.RUNNING, Instant.EPOCH)));
        TaskEntity a =
                new TaskEntity(
                        new TaskEntityId("a", "wf-1"),
                        TaskStatus.PENDING,
                        0,
                        "p1",
                        "c1",
                        List.of());
        TaskEntity b =
                new TaskEntity(
                        new TaskEntityId("b", "wf-1"),
                        TaskStatus.PENDING,
                        0,
                        "p2",
                        "c2",
                        List.of("a"));
        when(taskRepository.findByWorkflowId("wf-1")).thenReturn(List.of(a, b));

        Task runnableA = Task.pending("a", "wf-1", List.of(), "p1", "c1");
        Task runnableB = Task.pending("b", "wf-1", List.of("a"), "p2", "c2");
        when(runnableTaskSelector.findRunnableTasks(any())).thenReturn(List.of(runnableA, runnableB));

        service.triggerExecution("wf-1");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(workerTaskDispatcher, Mockito.times(2)).dispatch(captor.capture());
        List<Task> dispatched = captor.getAllValues();
        // Order follows runnable list order
        org.junit.jupiter.api.Assertions.assertEquals("a", dispatched.get(0).getTaskId());
        org.junit.jupiter.api.Assertions.assertEquals("b", dispatched.get(1).getTaskId());
        org.junit.jupiter.api.Assertions.assertEquals("wf-1", dispatched.get(0).getWorkflowId());
    }
}

