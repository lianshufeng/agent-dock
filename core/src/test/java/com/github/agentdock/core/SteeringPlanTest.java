package com.github.agentdock.core;

import com.github.agentdock.core.model.IntentCandidate;
import com.github.agentdock.core.planning.*;
import com.github.agentdock.core.steering.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SteeringPlanTest {
    private final AiExecutionEngine engine = new AiExecutionEngine(null, null, null, null, null, null);
    private final PlanPatchApplier applier = new PlanPatchApplier();

    @Test
    void additionalIntentKeepsOriginalPendingWorkAndCompletedResult() {
        ExecutionPlan plan = plan();
        plan.getNodes().get("data").setStatus(PlanNodeStatus.SUCCESS);
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.ADD, List.of(),
                List.of(intent("air", "空气质量", List.of("data"))), "补充空气质量", null);

        apply(plan, decision);

        assertEquals(PlanNodeStatus.SUCCESS, plan.getNodes().get("data").getStatus());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("advice").getStatus());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("p2-intent-0").getStatus());
        assertEquals(List.of("data"), plan.getNodes().get("p2-intent-0").getIntent().getDependsOn());
    }

    @Test
    void additionalIntentCanRunBeforeAnExistingPendingNode() {
        ExecutionPlan plan = plan();
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.ADD, List.of("advice"),
                List.of(intent("air", "空气质量", List.of("data"))), "先查询空气质量", null);

        apply(plan, decision);

        assertEquals(List.of("data", "p2-intent-0"),
                plan.getNodes().get("advice").getIntent().getDependsOn());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("report").getStatus());
    }

    @Test
    void unrelatedAdditionalIntentCannotBecomeDependencyOfIndependentOldTask() {
        ExecutionPlan plan = ExecutionPlan.from("independent", List.of(intent("old", "计算 12 加 13", List.of())));
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.ADD, List.of("old"),
                List.of(intent("new", "计算 20 加 22", List.of())), "另外计算", null);

        apply(plan, decision);

        assertEquals(List.of(), plan.getNodes().get("old").getIntent().getDependsOn());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("p2-intent-0").getStatus());
    }

    @Test
    void explicitReplacementKeepsUpstreamAndDownstreamDependencies() {
        ExecutionPlan plan = plan();
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.REPLACE, List.of("advice"),
                List.of(intent("new", "上海建议", List.of())), "改成上海", null);

        apply(plan, decision);

        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("data").getStatus());
        assertEquals("上海建议", plan.getNodes().get("advice").getIntent().getDescription());
        assertEquals(List.of("data"), plan.getNodes().get("advice").getIntent().getDependsOn());
        assertEquals(List.of("advice"), plan.getNodes().get("report").getIntent().getDependsOn());
    }

    @Test
    void updateOnlyChangesSelectedPendingTaskAndKeepsDownstream() {
        ExecutionPlan plan = plan();
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.UPDATE, List.of("advice"),
                List.of(intent("updated", "仅参考官方渠道生成建议", List.of())), "收紧条件", null);

        apply(plan, decision);

        assertEquals("仅参考官方渠道生成建议", plan.getNodes().get("advice").getIntent().getDescription());
        assertEquals(List.of("data"), plan.getNodes().get("advice").getIntent().getDependsOn());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("report").getStatus());
    }

    @Test
    void explicitCancellationIncludesOnlyPendingDescendants() {
        ExecutionPlan plan = plan();
        plan.getNodes().get("data").setStatus(PlanNodeStatus.SUCCESS);
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.CANCEL, List.of("advice"),
                List.of(), "不要建议", null);

        apply(plan, decision);

        assertEquals(PlanNodeStatus.SUCCESS, plan.getNodes().get("data").getStatus());
        assertEquals(PlanNodeStatus.CANCELLED, plan.getNodes().get("advice").getStatus());
        assertEquals(PlanNodeStatus.CANCELLED, plan.getNodes().get("report").getStatus());
    }

    @Test
    void invalidTargetAndDependencyLeavePlanUnchanged() {
        ExecutionPlan plan = plan();
        plan.getNodes().get("data").setStatus(PlanNodeStatus.SUCCESS);
        SteeringPlanDecision invalidTarget = new SteeringPlanDecision(SteeringAction.CANCEL,
                List.of("data"), List.of(), "", null);
        assertThrows(IllegalArgumentException.class, () -> apply(plan, invalidTarget));
        SteeringPlanDecision invalidDependency = new SteeringPlanDecision(SteeringAction.ADD,
                List.of(), List.of(intent("bad", "错误任务", List.of("missing"))), "", null);
        assertThrows(IllegalArgumentException.class, () -> apply(plan, invalidDependency));
        assertEquals(1, plan.getVersion());
        assertEquals(3, plan.getNodes().size());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("advice").getStatus());
    }

    @Test
    void legacyRollbackCancelsAllPendingNodes() {
        ExecutionPlan plan = plan();
        SteeringPlanDecision decision = new SteeringPlanDecision(SteeringAction.ADD, List.of(),
                List.of(intent("air", "空气质量", List.of())), "旧策略", null);

        applier.apply(plan, engine.legacySteeringPatch(decision, plan, 2),
                Set.of("TASK"), Integer.MAX_VALUE, false);

        assertEquals(PlanNodeStatus.CANCELLED, plan.getNodes().get("data").getStatus());
        assertEquals(PlanNodeStatus.CANCELLED, plan.getNodes().get("advice").getStatus());
        assertEquals(PlanNodeStatus.CANCELLED, plan.getNodes().get("report").getStatus());
        assertEquals(PlanNodeStatus.PENDING, plan.getNodes().get("p2-intent-0").getStatus());
    }

    private void apply(ExecutionPlan plan, SteeringPlanDecision decision) {
        applier.apply(plan, engine.steeringPatch(decision, plan, plan.getVersion() + 1),
                Set.of("TASK"), Integer.MAX_VALUE, false);
    }

    private ExecutionPlan plan() {
        return ExecutionPlan.from("test", List.of(
                intent("data", "查询数据", List.of()),
                intent("advice", "生成建议", List.of("data")),
                intent("report", "生成报告", List.of("advice"))));
    }

    private IntentCandidate intent(String id, String description, List<String> dependencies) {
        IntentCandidate intent = new IntentCandidate();
        intent.setId(id); intent.setCode("TASK"); intent.setDescription(description);
        intent.setExpectedResult(description + "完成"); intent.setDependsOn(dependencies);
        return intent;
    }
}
