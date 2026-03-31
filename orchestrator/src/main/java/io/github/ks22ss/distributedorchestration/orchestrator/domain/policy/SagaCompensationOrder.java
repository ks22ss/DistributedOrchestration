package io.github.ks22ss.distributedorchestration.orchestrator.domain.policy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reverse topological order over SUCCESS tasks: compensate dependents before dependencies. */
public final class SagaCompensationOrder {

    private SagaCompensationOrder() {}

    /**
     * @param successTaskDependencies map of successful taskId to its dependency taskIds (only within the successful set)
     * @return taskIds in reverse topological order (dependents first)
     */
    public static List<String> reverseTopologicalSuccessOrder(Map<String, List<String>> successTaskDependencies) {
        if (successTaskDependencies.isEmpty()) {
            return List.of();
        }

        Set<String> ids = new HashSet<>(successTaskDependencies.keySet());
        Map<String, Integer> indegree = new HashMap<>();
        for (String id : ids) {
            int c = 0;
            for (String dep : successTaskDependencies.getOrDefault(id, List.of())) {
                if (ids.contains(dep)) {
                    c++;
                }
            }
            indegree.put(id, c);
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String id : ids) {
            if (indegree.get(id) == 0) {
                queue.add(id);
            }
        }

        List<String> forward = new ArrayList<>(ids.size());
        while (!queue.isEmpty()) {
            String u = queue.removeFirst();
            forward.add(u);

            // For each v that depends on u, decrement indegree(v)
            for (String v : ids) {
                if (successTaskDependencies.getOrDefault(v, List.of()).contains(u)) {
                    int next = indegree.get(v) - 1;
                    indegree.put(v, next);
                    if (next == 0) {
                        queue.add(v);
                    }
                }
            }
        }

        if (forward.size() != ids.size()) {
            throw new IllegalStateException("success subgraph is not acyclic or inconsistent with dependencies");
        }

        List<String> reverse = new ArrayList<>(forward.size());
        for (int i = forward.size() - 1; i >= 0; i--) {
            reverse.add(forward.get(i));
        }
        return reverse;
    }
}

