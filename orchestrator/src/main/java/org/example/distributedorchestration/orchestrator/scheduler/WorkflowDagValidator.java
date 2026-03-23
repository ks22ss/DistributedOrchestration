package org.example.distributedorchestration.orchestrator.scheduler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.example.distributedorchestration.common.model.Task;
import org.springframework.stereotype.Component;

/**
 * Validates that workflow tasks form a DAG before orchestration starts.
 */
@Component
public class WorkflowDagValidator {

    /**
     * Validates the incoming task graph and throws when it is not executable.
     *
     * @param tasks map of taskId to task
     */
    public void validateOrThrow(Map<String, Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new InvalidWorkflowException("Workflow must contain at least one task");
        }
        validateDependenciesExist(tasks);
        if (hasCycle(tasks)) {
            throw new InvalidWorkflowException("Workflow contains a cycle and is not a DAG");
        }
    }

    /**
     * Detects whether the task graph has a cycle using DFS.
     *
     * @param tasks map of taskId to task
     * @return true when the graph contains at least one cycle
     */
    public boolean hasCycle(Map<String, Task> tasks) {
        Set<String> visited = new HashSet<>();
        Set<String> inRecursionStack = new HashSet<>();
        for (String taskId : tasks.keySet()) {
            if (!visited.contains(taskId) && dfsHasCycle(taskId, tasks, visited, inRecursionStack)) {
                return true;
            }
        }
        return false;
    }

    private void validateDependenciesExist(Map<String, Task> tasks) {
        for (Task task : tasks.values()) {
            for (String dependencyId : task.getDependencies()) {
                if (!tasks.containsKey(dependencyId)) {
                    throw new InvalidWorkflowException(
                            "Task '%s' depends on unknown task '%s'".formatted(task.getTaskId(), dependencyId)
                    );
                }
            }
        }
    }

    private boolean dfsHasCycle(
            String taskId,
            Map<String, Task> tasks,
            Set<String> visited,
            Set<String> inRecursionStack
    ) {
        visited.add(taskId);
        inRecursionStack.add(taskId);

        Task task = tasks.get(taskId);
        for (String dependencyId : task.getDependencies()) {
            if (!visited.contains(dependencyId) && dfsHasCycle(dependencyId, tasks, visited, inRecursionStack)) {
                return true;
            }
            if (inRecursionStack.contains(dependencyId)) {
                return true;
            }
        }

        inRecursionStack.remove(taskId);
        return false;
    }
}
