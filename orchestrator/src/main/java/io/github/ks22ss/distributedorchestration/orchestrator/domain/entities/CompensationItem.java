package io.github.ks22ss.distributedorchestration.orchestrator.domain.entities;

import io.github.ks22ss.distributedorchestration.orchestrator.adapters.data_access.sql.entity.TaskEntityId;

public record CompensationItem(TaskEntityId taskId, String workflowId, String compensationPayload) {}

