package io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.application.port.in.WorkflowExecutionUseCase;
import java.util.List;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DispatchRetrySchedulerTest {

    private final TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
    private final WorkflowExecutionUseCase executionUseCase = Mockito.mock(WorkflowExecutionUseCase.class);
    private final DispatchRetryScheduler scheduler = new DispatchRetryScheduler(taskRepository, executionUseCase);

    @Test
    void dispatchDueRetriesTriggersExecutionForEachWorkflowId() {
        when(taskRepository.findDistinctWorkflowIdsWithRetriesDue(
                        ArgumentMatchers.eq(TaskStatus.PENDING), ArgumentMatchers.any()))
                .thenReturn(List.of("wf-1", "wf-2"));

        scheduler.dispatchDueRetries();

        verify(executionUseCase).triggerExecution("wf-1");
        verify(executionUseCase).triggerExecution("wf-2");
    }

    @Test
    void dispatchDueRetriesContinuesWhenOneTriggerThrows() {
        when(taskRepository.findDistinctWorkflowIdsWithRetriesDue(
                        ArgumentMatchers.eq(TaskStatus.PENDING), ArgumentMatchers.any()))
                .thenReturn(List.of("wf-1", "wf-2"));
        doThrow(new RuntimeException("boom")).when(executionUseCase).triggerExecution("wf-1");

        scheduler.dispatchDueRetries();

        verify(executionUseCase).triggerExecution("wf-1");
        verify(executionUseCase).triggerExecution("wf-2");
    }
}

