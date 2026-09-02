package com.github.agentdock.core.planning;

import com.github.agentdock.core.model.IntentCandidate;
import lombok.Data;

import java.util.*;

/** 可版本化的运行时执行计划；已完成节点不可被补丁修改。 */
@Data
public final class ExecutionPlan {
    private String id;
    private int version = 1;
    private int replanCount;
    private LinkedHashMap<String, PlanNode> nodes = new LinkedHashMap<>();

    public static ExecutionPlan from(String executionId, List<IntentCandidate> intents) {
        ExecutionPlan plan = new ExecutionPlan();
        plan.setId("plan-" + executionId);
        Objects.requireNonNullElse(intents, List.<IntentCandidate>of()).forEach(intent ->
                plan.nodes.put(intent.getId(), new PlanNode(intent)));
        return plan;
    }

    public List<PlanNode> pendingNodes() {
        return nodes.values().stream().filter(node -> node.getStatus() == PlanNodeStatus.PENDING).toList();
    }

    public List<PlanNode> readyNodes() {
        return pendingNodes().stream().filter(node -> node.getIntent().getDependsOn().stream().allMatch(id -> {
            PlanNode dependency = nodes.get(id);
            return dependency != null && dependency.getStatus() != PlanNodeStatus.PENDING
                    && dependency.getStatus() != PlanNodeStatus.RUNNING;
        })).toList();
    }

    public List<IntentCandidate> orderedIntents() {
        return nodes.values().stream().filter(node -> node.getStatus() != PlanNodeStatus.CANCELLED)
                .map(PlanNode::getIntent).toList();
    }
}
