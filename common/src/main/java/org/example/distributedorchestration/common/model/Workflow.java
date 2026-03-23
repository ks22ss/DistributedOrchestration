package org.example.distributedorchestration.common.model;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public class Workflow {
    private final String workflowId;
    @Getter(AccessLevel.NONE)
    private final Map<String, Task> tasks;
    private @NonNull WorkflowStatus status;
    private final Instant createdAt;

    public Workflow(String workflowId, Map<String, Task> tasks, WorkflowStatus status) {
        this.workflowId = requireNonBlank(workflowId, "workflowId");
        this.tasks = tasks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tasks);
        this.status = status == null ? WorkflowStatus.PENDING : status;
        this.createdAt = Instant.now();
    }

    public static Workflow pending(String workflowId, Map<String, Task> tasks) {
        return new Workflow(workflowId, tasks, WorkflowStatus.PENDING);
    }

    public Map<String, Task> getTasks() {
        return Map.copyOf(tasks);
    }

    public Collection<Task> allTasks() {
        return tasks.values();
    }

    public List<Task> successfulTasks() {
        return tasks.values().stream()
                .filter(task -> task.getStatus() == TaskStatus.SUCCESS)
                .toList();
    }

    public Task task(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Unknown taskId: " + taskId);
        }
        return task;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
