package io.github.ks22ss.distributedorchestration.orchestrator.application.event;

/**
 * Published when a task is persisted as SUCCESS so dependent tasks can be scheduled.
 *
 * @param workflowId workflow identifier
 * @param taskId completed task identifier
 */
public record TaskCompletedEvent(String workflowId, String taskId) {}

