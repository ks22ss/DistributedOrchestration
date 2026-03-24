package org.example.distributedorchestration.worker.domain.model;

public record ExecuteTaskCommand(String taskId, String payload) {}
