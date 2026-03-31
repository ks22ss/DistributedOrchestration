package io.github.ks22ss.distributedorchestration.worker.application.command;

public record ExecuteTaskCommand(String taskId, String payload) {}

