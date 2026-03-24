package org.example.distributedorchestration.orchestrator.domain.model;

import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;

public record CompensationItem(TaskEntityId taskId, String workflowId, String compensationPayload) {}
