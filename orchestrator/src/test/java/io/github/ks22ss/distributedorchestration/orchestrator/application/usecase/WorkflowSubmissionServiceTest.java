package io.github.ks22ss.distributedorchestration.orchestrator.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.command.SubmitWorkflowTaskCommand;
import io.github.ks22ss.distributedorchestration.orchestrator.application.event.WorkflowSubmittedEvent;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.WorkflowEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.WorkflowJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.DuplicateWorkflowException;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.InvalidWorkflowException;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.policy.WorkflowDagValidator;
import java.util.List;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.common.model.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class WorkflowSubmissionServiceTest {

    private final WorkflowDagValidator dagValidator = Mockito.mock(WorkflowDagValidator.class);
    private final WorkflowJpaRepository workflowRepository = Mockito.mock(WorkflowJpaRepository.class);
    private final TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    private final WorkflowSubmissionService service =
            new WorkflowSubmissionService(dagValidator, workflowRepository, taskRepository, eventPublisher);

    @Test
    void submitThrowsWhenWorkflowAlreadyExists() {
        when(workflowRepository.existsById("wf-1")).thenReturn(true);
        SubmitWorkflowCommand command =
                new SubmitWorkflowCommand("wf-1", List.of(new SubmitWorkflowTaskCommand("t1", List.of(), "p", null)));

        assertThrows(DuplicateWorkflowException.class, () -> service.submit(command));

        verify(workflowRepository, never()).save(any(WorkflowEntity.class));
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submitDoesNotPersistOrPublishWhenDagValidationFails() {
        when(workflowRepository.existsById("wf-1")).thenReturn(false);
        SubmitWorkflowCommand command =
                new SubmitWorkflowCommand("wf-1", List.of(new SubmitWorkflowTaskCommand("t1", List.of("missing"), "p", null)));
        Mockito.doThrow(new InvalidWorkflowException("bad dag")).when(dagValidator).validateOrThrow(any());

        assertThrows(InvalidWorkflowException.class, () -> service.submit(command));

        verify(workflowRepository, never()).save(any(WorkflowEntity.class));
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submitPersistsWorkflowAndTasksAndPublishesEvent() {
        when(workflowRepository.existsById("wf-1")).thenReturn(false);
        SubmitWorkflowCommand command =
                new SubmitWorkflowCommand(
                        "wf-1",
                        List.of(
                                new SubmitWorkflowTaskCommand("a", List.of(), "p1", "c1"),
                                new SubmitWorkflowTaskCommand("b", List.of("a"), "p2", "c2")));

        var result = service.submit(command);

        assertEquals("wf-1", result.workflowId());
        assertEquals(WorkflowStatus.RUNNING.name(), result.status());

        ArgumentCaptor<WorkflowEntity> workflowCaptor = ArgumentCaptor.forClass(WorkflowEntity.class);
        verify(workflowRepository).save(workflowCaptor.capture());
        assertEquals("wf-1", workflowCaptor.getValue().getWorkflowId());
        assertEquals(WorkflowStatus.RUNNING, workflowCaptor.getValue().getStatus());

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository, times(2)).save(taskCaptor.capture());
        var tasks = taskCaptor.getAllValues();
        assertEquals(2, tasks.size());
        assertEquals(TaskStatus.PENDING, tasks.get(0).getStatus());
        assertEquals(TaskStatus.PENDING, tasks.get(1).getStatus());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        Object event = eventCaptor.getValue();
        assertEquals(WorkflowSubmittedEvent.class, event.getClass());
        assertEquals("wf-1", ((WorkflowSubmittedEvent) event).workflowId());
    }
}

