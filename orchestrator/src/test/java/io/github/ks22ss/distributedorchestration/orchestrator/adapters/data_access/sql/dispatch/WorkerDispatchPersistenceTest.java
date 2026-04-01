package io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntity;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.repository.TaskJpaRepository;
import io.github.ks22ss.distributedorchestration.orchestrator.application.event.TaskCompletedEvent;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WorkerDispatchPersistenceTest {

    private final TaskJpaRepository taskRepository = Mockito.mock(TaskJpaRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    private final WorkerDispatchPersistence persistence =
            new WorkerDispatchPersistence(taskRepository, eventPublisher);

    @AfterEach
    void cleanupTxSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void tryBeginDispatchReturnsFalseWhenNotPending() {
        TaskEntityId id = new TaskEntityId("t1", "wf-1");
        TaskEntity entity = new TaskEntity(id, TaskStatus.SUCCESS, 0, "p", null, List.of());
        when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

        boolean claim = persistence.tryBeginDispatch(id, Instant.EPOCH);

        assertFalse(claim);
    }

    @Test
    void tryBeginDispatchReturnsFalseWhenNextRetryAtInFuture() {
        TaskEntityId id = new TaskEntityId("t1", "wf-1");
        TaskEntity entity = new TaskEntity(id, TaskStatus.PENDING, 0, "p", null, List.of());
        entity.setNextRetryAt(Instant.EPOCH.plusSeconds(60));
        when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

        boolean claim = persistence.tryBeginDispatch(id, Instant.EPOCH);

        assertFalse(claim);
    }

    @Test
    void tryBeginDispatchSetsLeaseWhenClaimable() throws Exception {
        setField(persistence, "inFlightLeaseSeconds", 120);
        TaskEntityId id = new TaskEntityId("t1", "wf-1");
        TaskEntity entity = new TaskEntity(id, TaskStatus.PENDING, 0, "p", null, List.of());
        when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

        Instant now = Instant.parse("2020-01-01T00:00:00Z");
        boolean claim = persistence.tryBeginDispatch(id, now);

        assertTrue(claim);
        assertEquals(now.plusSeconds(120), entity.getNextRetryAt());
        verify(taskRepository).save(entity);
    }

    @Test
    void recordFailureAndScheduleRetryAppliesExponentialBackoff() throws Exception {
        setField(persistence, "maxRetries", 5);
        TaskEntityId id = new TaskEntityId("t1", "wf-1");
        TaskEntity entity = new TaskEntity(id, TaskStatus.PENDING, 0, "p", null, List.of());
        when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

        Instant now = Instant.parse("2020-01-01T00:00:00Z");
        WorkerDispatchPersistence.BackoffOutcome outcome = persistence.recordFailureAndScheduleRetry(id, now);

        assertEquals(1, outcome.delaySeconds());
        assertFalse(outcome.exhausted());
        assertEquals(1, entity.getRetryCount());
        assertEquals(TaskStatus.PENDING, entity.getStatus());
        assertEquals(now.plusSeconds(1), entity.getNextRetryAt());
        verify(taskRepository).save(entity);
    }

    @Test
    void recordFailureAndScheduleRetryMarksFailedWhenExhausted() throws Exception {
        setField(persistence, "maxRetries", 0);
        TaskEntityId id = new TaskEntityId("t1", "wf-1");
        TaskEntity entity = new TaskEntity(id, TaskStatus.PENDING, 0, "p", null, List.of());
        when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

        WorkerDispatchPersistence.BackoffOutcome outcome =
                persistence.recordFailureAndScheduleRetry(id, Instant.EPOCH);

        assertTrue(outcome.exhausted());
        assertEquals(TaskStatus.FAILED, entity.getStatus());
        assertNull(entity.getNextRetryAt());
    }

    @Test
    void markSuccessPublishesTaskCompletedEventAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TaskEntityId id = new TaskEntityId("t1", "wf-1");
        TaskEntity entity = new TaskEntity(id, TaskStatus.PENDING, 3, "p", null, List.of());
        entity.setNextRetryAt(Instant.EPOCH.plusSeconds(60));
        when(taskRepository.findById(id)).thenReturn(Optional.of(entity));

        persistence.markSuccess(id);

        assertEquals(TaskStatus.SUCCESS, entity.getStatus());
        assertEquals(0, entity.getRetryCount());
        assertNull(entity.getNextRetryAt());
        verify(taskRepository).save(entity);

        var syncs = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, syncs.size());
        syncs.getFirst().afterCommit();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        Object event = captor.getValue();
        assertEquals(TaskCompletedEvent.class, event.getClass());
        assertEquals("wf-1", ((TaskCompletedEvent) event).workflowId());
        assertEquals("t1", ((TaskCompletedEvent) event).taskId());
    }

    private static void setField(Object target, String field, int value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(target, value);
    }
}

