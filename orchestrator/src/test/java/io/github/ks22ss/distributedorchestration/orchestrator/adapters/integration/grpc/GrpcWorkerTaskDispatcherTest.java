package io.github.ks22ss.distributedorchestration.orchestrator.adapters.integration.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.dispatch.WorkerDispatchPersistence;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;
import io.github.ks22ss.distributedorchestration.orchestrator.adapters.observability.OrchestrationMetrics;
import io.github.ks22ss.distributedorchestration.orchestrator.application.usecase.WorkflowCompensationAsyncRunner;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.List;
import org.example.distributedorchestration.common.model.Task;
import org.example.distributedorchestration.common.worker.v1.TaskRequest;
import org.example.distributedorchestration.common.worker.v1.TaskResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GrpcWorkerTaskDispatcherTest {

    private final ResilientWorkerGrpcClient resilientWorkerClient = Mockito.mock(ResilientWorkerGrpcClient.class);
    private final WorkerDispatchPersistence persistence = Mockito.mock(WorkerDispatchPersistence.class);
    private final WorkflowCompensationAsyncRunner compensationAsyncRunner =
            Mockito.mock(WorkflowCompensationAsyncRunner.class);
    private final OrchestrationMetrics metrics = Mockito.mock(OrchestrationMetrics.class);

    private final GrpcWorkerTaskDispatcher dispatcher =
            new GrpcWorkerTaskDispatcher(resilientWorkerClient, persistence, compensationAsyncRunner, metrics);

    @Test
    void dispatchSkipsWhenNotClaimable() {
        Task task = Task.pending("t1", "wf-1", List.of(), "p", "c");
        when(persistence.tryBeginDispatch(any(TaskEntityId.class), any(Instant.class))).thenReturn(false);

        dispatcher.dispatch(task);

        verify(resilientWorkerClient, never()).executeTask(any(TaskRequest.class));
        verify(persistence, never()).markSuccess(any());
        verify(persistence, never()).recordFailureAndScheduleRetry(any(), any());
    }

    @Test
    void dispatchMarksSuccessWhenWorkerReturnsSuccess() {
        Task task = Task.pending("t1", "wf-1", List.of(), "p", "c");
        when(persistence.tryBeginDispatch(any(TaskEntityId.class), any(Instant.class))).thenReturn(true);
        when(resilientWorkerClient.executeTask(any(TaskRequest.class)))
                .thenReturn(TaskResponse.newBuilder().setSuccess(true).setMessage("ok").build());

        dispatcher.dispatch(task);

        verify(persistence).markSuccess(new TaskEntityId("t1", "wf-1"));
        verify(persistence, never()).recordFailureAndScheduleRetry(any(), any());
        verify(compensationAsyncRunner, never()).triggerCompensation(any());
    }

    @Test
    void dispatchSchedulesRetryWhenWorkerReturnsLogicalFailure() {
        Task task = Task.pending("t1", "wf-1", List.of(), "p", "c");
        when(persistence.tryBeginDispatch(any(TaskEntityId.class), any(Instant.class))).thenReturn(true);
        when(resilientWorkerClient.executeTask(any(TaskRequest.class)))
                .thenReturn(TaskResponse.newBuilder().setSuccess(false).setMessage("nope").build());
        when(persistence.recordFailureAndScheduleRetry(any(), any()))
                .thenReturn(new WorkerDispatchPersistence.BackoffOutcome(1, false));

        dispatcher.dispatch(task);

        verify(persistence).recordFailureAndScheduleRetry(any(TaskEntityId.class), any(Instant.class));
        verify(compensationAsyncRunner, never()).triggerCompensation(any());
    }

    @Test
    void dispatchTriggersCompensationWhenRetriesExhausted() {
        Task task = Task.pending("t1", "wf-1", List.of(), "p", "c");
        when(persistence.tryBeginDispatch(any(TaskEntityId.class), any(Instant.class))).thenReturn(true);
        when(resilientWorkerClient.executeTask(any(TaskRequest.class)))
                .thenReturn(TaskResponse.newBuilder().setSuccess(false).setMessage("nope").build());
        when(persistence.recordFailureAndScheduleRetry(any(), any()))
                .thenReturn(new WorkerDispatchPersistence.BackoffOutcome(0, true));

        dispatcher.dispatch(task);

        verify(compensationAsyncRunner).triggerCompensation("wf-1");
    }

    @Test
    void dispatchSchedulesRetryWhenCircuitBreakerOpen() {
        Task task = Task.pending("t1", "wf-1", List.of(), "p", "c");
        when(persistence.tryBeginDispatch(any(TaskEntityId.class), any(Instant.class))).thenReturn(true);
        when(resilientWorkerClient.executeTask(any(TaskRequest.class)))
                .thenThrow(
                        CallNotPermittedException.createCallNotPermittedException(
                                CircuitBreaker.ofDefaults("worker")));
        when(persistence.recordFailureAndScheduleRetry(any(), any()))
                .thenReturn(new WorkerDispatchPersistence.BackoffOutcome(1, false));

        dispatcher.dispatch(task);

        verify(persistence).recordFailureAndScheduleRetry(any(TaskEntityId.class), any(Instant.class));
    }

    @Test
    void dispatchSchedulesRetryWhenGrpcThrows() {
        Task task = Task.pending("t1", "wf-1", List.of(), null, "c");
        when(persistence.tryBeginDispatch(any(TaskEntityId.class), any(Instant.class))).thenReturn(true);
        when(resilientWorkerClient.executeTask(any(TaskRequest.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        when(persistence.recordFailureAndScheduleRetry(any(), any()))
                .thenReturn(new WorkerDispatchPersistence.BackoffOutcome(1, false));

        dispatcher.dispatch(task);

        ArgumentCaptor<TaskRequest> requestCaptor = ArgumentCaptor.forClass(TaskRequest.class);
        verify(resilientWorkerClient).executeTask(requestCaptor.capture());
        // payload null is mapped to empty string
        org.junit.jupiter.api.Assertions.assertEquals("", requestCaptor.getValue().getPayload());

        verify(persistence).recordFailureAndScheduleRetry(any(TaskEntityId.class), any(Instant.class));
    }
}

