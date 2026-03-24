package org.example.distributedorchestration.orchestrator.saga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.distributedorchestration.common.model.TaskStatus;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntity;
import org.example.distributedorchestration.orchestrator.persistence.entity.TaskEntityId;
import org.junit.jupiter.api.Test;

class SagaCompensationOrderTest {

    @Test
    void linearChainCompensatesDependentBeforeDependency() {
        TaskEntity a = task("wf", "A", List.of());
        TaskEntity b = task("wf", "B", List.of("A"));
        List<TaskEntity> order = SagaCompensationOrder.reverseTopologicalSuccessOrder(List.of(a, b));
        assertEquals(2, order.size());
        assertEquals("B", order.get(0).getId().getTaskId());
        assertEquals("A", order.get(1).getId().getTaskId());
    }

    @Test
    void diamondCompensatesDBeforeBAndCBeforeA() {
        TaskEntity a = task("wf", "A", List.of());
        TaskEntity b = task("wf", "B", List.of("A"));
        TaskEntity c = task("wf", "C", List.of("A"));
        TaskEntity d = task("wf", "D", List.of("B", "C"));
        List<TaskEntity> order =
                SagaCompensationOrder.reverseTopologicalSuccessOrder(List.of(a, b, c, d));
        assertEquals(4, order.size());
        assertEquals("D", order.get(0).getId().getTaskId());
        assertEquals("C", order.get(1).getId().getTaskId());
        assertEquals("B", order.get(2).getId().getTaskId());
        assertEquals("A", order.get(3).getId().getTaskId());
    }

    @Test
    void twoIndependentRootsReverseOrderMatchesForwardListOrder() {
        TaskEntity a = task("wf", "A", List.of());
        TaskEntity b = task("wf", "B", List.of());
        List<TaskEntity> order = SagaCompensationOrder.reverseTopologicalSuccessOrder(List.of(a, b));
        assertEquals(2, order.size());
        assertEquals("B", order.get(0).getId().getTaskId());
        assertEquals("A", order.get(1).getId().getTaskId());
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertTrue(SagaCompensationOrder.reverseTopologicalSuccessOrder(List.of()).isEmpty());
    }

    private static TaskEntity task(String wf, String id, List<String> deps) {
        TaskEntity e = new TaskEntity();
        e.setId(new TaskEntityId(id, wf));
        e.setStatus(TaskStatus.SUCCESS);
        e.setRetryCount(0);
        e.setDependencies(deps);
        return e;
    }
}
