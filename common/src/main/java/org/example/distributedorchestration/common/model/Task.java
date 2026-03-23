package org.example.distributedorchestration.common.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public class Task {
    private final String taskId;
    private final String workflowId;
    @Getter(AccessLevel.NONE)
    private final List<String> dependencies;
    private @NonNull TaskStatus status;
    private int retryCount;
    private final String payload;

    public Task(
            String taskId,
            String workflowId,
            List<String> dependencies,
            TaskStatus status,
            int retryCount,
            String payload
    ) {
        this.taskId = requireNonBlank(taskId, "taskId");
        this.workflowId = requireNonBlank(workflowId, "workflowId");
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.status = status == null ? TaskStatus.PENDING : status;
        this.retryCount = Math.max(retryCount, 0);
        this.payload = payload;
    }

    public static Task pending(String taskId, String workflowId, List<String> dependencies, String payload) {
        return new Task(taskId, workflowId, dependencies, TaskStatus.PENDING, 0, payload);
    }

    public List<String> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    public boolean isTerminal() {
        return status == TaskStatus.SUCCESS || status == TaskStatus.COMPENSATED;
    }

    public boolean dependenciesSatisfiedBy(List<Task> completedTasks) {
        if (dependencies.isEmpty()) {
            return true;
        }

        List<String> completedTaskIds = new ArrayList<>(completedTasks.size());
        for (Task task : completedTasks) {
            if (task.getStatus() == TaskStatus.SUCCESS) {
                completedTaskIds.add(task.getTaskId());
            }
        }
        return completedTaskIds.containsAll(dependencies);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value;
    }
}
