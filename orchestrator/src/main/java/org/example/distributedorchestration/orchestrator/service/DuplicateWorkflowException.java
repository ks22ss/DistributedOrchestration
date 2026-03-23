package org.example.distributedorchestration.orchestrator.service;

/**
 * Raised when a workflow id is already persisted.
 */
public class DuplicateWorkflowException extends RuntimeException {

    public DuplicateWorkflowException(String message) {
        super(message);
    }
}
