package io.github.ks22ss.distributedorchestration.worker.application.command;

public record CompensateTaskCommand(String taskId, String compensationPayload) {}

