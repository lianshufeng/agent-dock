package com.github.agentdock.core.planning;

import com.github.agentdock.core.model.IntentCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanPatchApplierTest {
    @Test
    void userSteeringHasIndependentCounterFromAutomaticReplan() {
        ExecutionPlan plan = ExecutionPlan.from("execution", List.of(intent("old", "chat")));
        PlanPatchApplier applier = new PlanPatchApplier();

        applier.apply(plan, addPatch(intent("steered", "chat")), Set.of("chat"), 4, false);
        assertEquals(2, plan.getVersion());
        assertEquals(1, plan.getSteeringCount());
        assertEquals(0, plan.getReplanCount());

        applier.apply(plan, addPatch(intent("replanned", "chat")), Set.of("chat"), 4);
        assertEquals(3, plan.getVersion());
        assertEquals(1, plan.getSteeringCount());
        assertEquals(1, plan.getReplanCount());
    }

    @Test
    void legacyApplyRetainsVersionBasedReplanLimit() {
        ExecutionPlan plan = ExecutionPlan.from("execution", List.of(intent("old", "chat")));
        PlanPatchApplier applier = new PlanPatchApplier();

        applier.apply(plan, addPatch(intent("replanned-1", "chat")), Set.of("chat"), 4);
        applier.apply(plan, addPatch(intent("replanned-2", "chat")), Set.of("chat"), 4);
        applier.apply(plan, addPatch(intent("replanned-3", "chat")), Set.of("chat"), 4);

        assertEquals(4, plan.getVersion());
        assertEquals(3, plan.getReplanCount());
        assertThrows(IllegalArgumentException.class,
                () -> applier.apply(plan, addPatch(intent("replanned-4", "chat")), Set.of("chat"), 4));
    }

    private PlanPatch addPatch(IntentCandidate intent) {
        PlanPatchOperation operation = new PlanPatchOperation();
        operation.setType(PlanPatchType.ADD_NODE);
        operation.setNode(intent);
        PlanPatch patch = new PlanPatch();
        patch.setOperations(List.of(operation));
        return patch;
    }

    private IntentCandidate intent(String id, String code) {
        IntentCandidate value = new IntentCandidate();
        value.setId(id);
        value.setCode(code);
        value.setExpectedResult("完成");
        value.setDependsOn(List.of());
        return value;
    }
}
