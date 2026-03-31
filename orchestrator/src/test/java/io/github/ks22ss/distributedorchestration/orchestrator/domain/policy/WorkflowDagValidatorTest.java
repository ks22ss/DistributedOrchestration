package io.github.ks22ss.distributedorchestration.orchestrator.domain.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.InvalidWorkflowException;
import java.util.List;
import java.util.Map;
import org.example.distributedorchestration.common.model.Task;
import org.junit.jupiter.api.Test;

class WorkflowDagValidatorTest {

    private final WorkflowDagValidator validator = new WorkflowDagValidator();

    @Test
    void hasCycleReturnsFalseForValidDag() {
        Map<String, Task> tasks =
                Map.of(
                        "A", Task.pending("A", "wf-1", List.of(), "payload"),
                        "B", Task.pending("B", "wf-1", List.of("A"), "payload"),
                        "C", Task.pending("C", "wf-1", List.of("A"), "payload"));

        assertFalse(validator.hasCycle(tasks));
    }

    @Test
    void hasCycleReturnsTrueWhenCycleExists() {
        Map<String, Task> tasks =
                Map.of(
                        "A", Task.pending("A", "wf-1", List.of("C"), "payload"),
                        "B", Task.pending("B", "wf-1", List.of("A"), "payload"),
                        "C", Task.pending("C", "wf-1", List.of("B"), "payload"));

        assertTrue(validator.hasCycle(tasks));
    }

    @Test
    void validateOrThrowPassesForValidDag() {
        Map<String, Task> tasks =
                Map.of(
                        "A", Task.pending("A", "wf-1", List.of(), "payload"),
                        "B", Task.pending("B", "wf-1", List.of("A"), "payload"));

        assertDoesNotThrow(() -> validator.validateOrThrow(tasks));
    }

    @Test
    void validateOrThrowFailsWhenDependencyDoesNotExist() {
        Map<String, Task> tasks = Map.of("A", Task.pending("A", "wf-1", List.of("MISSING"), "payload"));

        assertThrows(InvalidWorkflowException.class, () -> validator.validateOrThrow(tasks));
    }

    @Test
    void validateOrThrowFailsWhenGraphHasCycle() {
        Map<String, Task> tasks =
                Map.of(
                        "A", Task.pending("A", "wf-1", List.of("B"), "payload"),
                        "B", Task.pending("B", "wf-1", List.of("A"), "payload"));

        assertThrows(InvalidWorkflowException.class, () -> validator.validateOrThrow(tasks));
    }
}

