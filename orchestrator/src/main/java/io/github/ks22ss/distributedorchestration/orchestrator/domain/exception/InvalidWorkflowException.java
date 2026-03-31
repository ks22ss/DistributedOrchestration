package io.github.ks22ss.distributedorchestration.orchestrator.domain.exception;

/** Raised when a workflow DAG is invalid and cannot be executed safely. */
public class InvalidWorkflowException extends RuntimeException {

    public InvalidWorkflowException(String message) {
        super(message);
    }
}

