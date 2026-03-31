package io.github.ks22ss.distributedorchestration.orchestrator.domain.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SagaCompensationOrderTest {

    @Test
    void linearChainCompensatesDependentBeforeDependency() {
        Map<String, List<String>> deps = Map.of("A", List.of(), "B", List.of("A"));
        List<String> order = SagaCompensationOrder.reverseTopologicalSuccessOrder(deps);
        assertEquals(List.of("B", "A"), order);
    }

    @Test
    void diamondCompensatesDBeforeBAndCBeforeA() {
        Map<String, List<String>> deps =
                Map.of(
                        "A", List.of(),
                        "B", List.of("A"),
                        "C", List.of("A"),
                        "D", List.of("B", "C"));
        List<String> order = SagaCompensationOrder.reverseTopologicalSuccessOrder(deps);
        assertEquals(List.of("D", "C", "B", "A"), order);
    }

    @Test
    void twoIndependentRootsReverseOrderMatchesForwardListOrder() {
        Map<String, List<String>> deps = Map.of("A", List.of(), "B", List.of());
        List<String> order = SagaCompensationOrder.reverseTopologicalSuccessOrder(deps);
        assertEquals(List.of("B", "A"), order);
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertTrue(SagaCompensationOrder.reverseTopologicalSuccessOrder(Map.of()).isEmpty());
    }
}

