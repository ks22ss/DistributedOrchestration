package org.example.distributedorchestration.orchestrator.domain.model;

import java.util.List;

public record CompensationStartResult(Type type, List<CompensationItem> items) {

    public enum Type {
        SKIP,
        NO_SUCCESS_TASKS,
        RUN_COMPENSATION
    }

    public static CompensationStartResult skip() {
        return new CompensationStartResult(Type.SKIP, List.of());
    }

    public static CompensationStartResult noSuccessTasks() {
        return new CompensationStartResult(Type.NO_SUCCESS_TASKS, List.of());
    }

    public static CompensationStartResult run(List<CompensationItem> items) {
        return new CompensationStartResult(Type.RUN_COMPENSATION, items);
    }
}
