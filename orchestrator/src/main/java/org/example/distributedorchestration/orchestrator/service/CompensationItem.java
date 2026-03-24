package org.example.distributedorchestration.orchestrator.service;

import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;

public record CompensationItem(TaskEntityId taskId, String workflowId, String compensationPayload) {}
