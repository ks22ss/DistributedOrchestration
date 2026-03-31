package io.github.ks22ss.distributedorchestration.orchestrator.domain.exception;

/** Raised when a workflow id is already persisted. */
public class DuplicateWorkflowException extends RuntimeException {

    public DuplicateWorkflowException(String message) {
        super(message);
    }
}

