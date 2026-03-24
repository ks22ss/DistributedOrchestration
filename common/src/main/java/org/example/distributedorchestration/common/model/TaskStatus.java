package org.example.distributedorchestration.common.model;

public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    COMPENSATED,
    /** Compensation RPC failed after retries (Step 11 hardening). */
    COMPENSATION_FAILED
}
