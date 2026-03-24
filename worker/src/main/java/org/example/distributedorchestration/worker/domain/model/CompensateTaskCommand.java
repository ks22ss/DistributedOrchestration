package org.example.distributedorchestration.worker.domain.model;

public record CompensateTaskCommand(String taskId, String compensationPayload) {}
