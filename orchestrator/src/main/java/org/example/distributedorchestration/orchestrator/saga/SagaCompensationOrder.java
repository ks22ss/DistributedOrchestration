package org.example.distributedorchestration.orchestrator.saga;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;

/**
 * Reverse topological order over SUCCESS tasks: compensate dependents before dependencies.
 */
public final class SagaCompensationOrder {

    private SagaCompensationOrder() {}

    public static List<TaskEntity> reverseTopologicalSuccessOrder(List<TaskEntity> successTasks) {
        if (successTasks.isEmpty()) {
            return List.of();
        }
        Set<String> ids = new HashSet<>();
        for (TaskEntity t : successTasks) {
            ids.add(t.getId().getTaskId());
        }
        Map<String, TaskEntity> byId = new HashMap<>();
        for (TaskEntity t : successTasks) {
            byId.put(t.getId().getTaskId(), t);
        }
        Map<String, Integer> indegree = new HashMap<>();
        for (TaskEntity t : successTasks) {
            int c = 0;
            for (String dep : t.getDependencies()) {
                if (ids.contains(dep)) {
                    c++;
                }
            }
            indegree.put(t.getId().getTaskId(), c);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (TaskEntity t : successTasks) {
            if (indegree.get(t.getId().getTaskId()) == 0) {
                queue.add(t.getId().getTaskId());
            }
        }
        List<String> forward = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.removeFirst();
            forward.add(u);
            for (TaskEntity v : successTasks) {
                if (v.getDependencies().contains(u)) {
                    String vid = v.getId().getTaskId();
                    int next = indegree.get(vid) - 1;
                    indegree.put(vid, next);
                    if (next == 0) {
                        queue.add(vid);
                    }
                }
            }
        }
        if (forward.size() != successTasks.size()) {
            throw new IllegalStateException("success subgraph is not acyclic or inconsistent with dependencies");
        }
        List<TaskEntity> reverse = new ArrayList<>(forward.size());
        for (int i = forward.size() - 1; i >= 0; i--) {
            reverse.add(byId.get(forward.get(i)));
        }
        return reverse;
    }
}
