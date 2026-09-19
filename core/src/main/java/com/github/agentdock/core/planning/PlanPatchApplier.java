package com.github.agentdock.core.planning;

import com.github.agentdock.core.model.IntentCandidate;

import java.util.*;

/** 原子校验并应用计划补丁，确保不改历史、不越过依赖且不产生环。 */
public final class PlanPatchApplier {
    public Set<String> apply(ExecutionPlan plan, PlanPatch patch, Set<String> allowedIntentCodes,
                             int maxPlanVersions) {
        return apply(plan, patch, allowedIntentCodes, maxPlanVersions, true);
    }

    /** 用户主动调整计划时不消耗系统自动重规划次数。 */
    public Set<String> apply(ExecutionPlan plan, PlanPatch patch, Set<String> allowedIntentCodes,
                             int maxPlanVersions, boolean automaticReplan) {
        if (plan == null || patch == null || patch.getOperations() == null || patch.getOperations().isEmpty())
            throw new IllegalArgumentException("计划补丁不能为空");
        // 旧实现以初始 version=1 计入上限，因此 maxPlanVersions=4 时最多允许 3 次自动重规划。
        // 用户 steering 使用独立计数，但旧 apply(...) 的次数语义必须保持不变。
        if (automaticReplan && plan.getReplanCount() >= Math.max(maxPlanVersions - 1, 0))
            throw new IllegalArgumentException("已达到最大自动重规划次数");
        LinkedHashMap<String, PlanNode> copy = copy(plan.getNodes());
        Set<String> resetNodeIds = new LinkedHashSet<>();
        for (PlanPatchOperation operation : patch.getOperations()) {
            if (operation == null || operation.getType() == null) throw new IllegalArgumentException("计划补丁操作无效");
            switch (operation.getType()) {
                case ADD_NODE -> add(copy, operation, allowedIntentCodes);
                case REPLACE_NODE -> replace(copy, operation, allowedIntentCodes, resetNodeIds);
                case UPDATE_DEPENDENCY -> updateDependencies(copy, operation);
                case CANCEL_NODE -> cancel(copy, operation);
            }
        }
        validateDependencies(copy);
        plan.setNodes(copy);
        plan.setVersion(plan.getVersion() + 1);
        if (automaticReplan) plan.setReplanCount(plan.getReplanCount() + 1);
        else plan.setSteeringCount(plan.getSteeringCount() + 1);
        return resetNodeIds;
    }

    private void add(Map<String, PlanNode> nodes, PlanPatchOperation operation, Set<String> allowedCodes) {
        IntentCandidate intent = requireIntent(operation.getNode(), allowedCodes);
        if (intent.getId() == null || intent.getId().isBlank() || nodes.containsKey(intent.getId()))
            throw new IllegalArgumentException("新增计划节点 ID 为空或重复");
        intent.setDependsOn(safe(operation.getDependsOn()).isEmpty() ? safe(intent.getDependsOn())
                : safe(operation.getDependsOn()));
        nodes.put(intent.getId(), new PlanNode(intent));
    }

    private void replace(Map<String, PlanNode> nodes, PlanPatchOperation operation, Set<String> allowedCodes,
                         Set<String> resetNodeIds) {
        PlanNode current = requireMutable(nodes, operation.getTargetNodeId());
        IntentCandidate replacement = requireIntent(operation.getNode(), allowedCodes);
        replacement.setId(current.getId());
        replacement.setDependsOn(safe(operation.getDependsOn()).isEmpty()
                ? safe(replacement.getDependsOn()) : safe(operation.getDependsOn()));
        PlanNode node = new PlanNode(replacement);
        node.setReplacesNodeId(current.getId());
        nodes.put(current.getId(), node);
        resetNodeIds.add(current.getId());
    }

    private void updateDependencies(Map<String, PlanNode> nodes, PlanPatchOperation operation) {
        PlanNode node = requireMutable(nodes, operation.getTargetNodeId());
        node.getIntent().setDependsOn(safe(operation.getDependsOn()));
    }

    private void cancel(Map<String, PlanNode> nodes, PlanPatchOperation operation) {
        PlanNode node = requireMutable(nodes, operation.getTargetNodeId());
        node.setStatus(PlanNodeStatus.CANCELLED);
    }

    private PlanNode requireMutable(Map<String, PlanNode> nodes, String id) {
        PlanNode node = nodes.get(id);
        if (node == null) throw new IllegalArgumentException("计划节点不存在: " + id);
        if (node.getStatus() == PlanNodeStatus.SUCCESS || node.getStatus() == PlanNodeStatus.RUNNING)
            throw new IllegalArgumentException("不能修改已完成或正在执行的计划节点: " + id);
        return node;
    }

    private IntentCandidate requireIntent(IntentCandidate intent, Set<String> allowedCodes) {
        if (intent == null || intent.getCode() == null || !allowedCodes.contains(intent.getCode()))
            throw new IllegalArgumentException("重规划返回了未注册意图");
        if (intent.getExpectedResult() == null || intent.getExpectedResult().isBlank())
            throw new IllegalArgumentException("重规划节点缺少预期结果");
        if (intent.getDependsOn() == null) intent.setDependsOn(List.of());
        return intent;
    }

    private void validateDependencies(Map<String, PlanNode> nodes) {
        for (PlanNode node : nodes.values()) for (String dependency : safe(node.getIntent().getDependsOn())) {
            if (!nodes.containsKey(dependency)) throw new IllegalArgumentException("计划依赖不存在: " + dependency);
            if (node.getId().equals(dependency)) throw new IllegalArgumentException("计划节点不能依赖自身: " + node.getId());
            if (node.getStatus() != PlanNodeStatus.CANCELLED
                    && nodes.get(dependency).getStatus() == PlanNodeStatus.CANCELLED)
                throw new IllegalArgumentException("有效计划节点不能依赖已取消节点: " + node.getId());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String id : nodes.keySet()) visit(id, nodes, visiting, visited);
    }

    private void visit(String id, Map<String, PlanNode> nodes, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("计划依赖存在环: " + id);
        for (String dependency : safe(nodes.get(id).getIntent().getDependsOn())) visit(dependency, nodes, visiting, visited);
        visiting.remove(id);
        visited.add(id);
    }

    private LinkedHashMap<String, PlanNode> copy(Map<String, PlanNode> source) {
        LinkedHashMap<String, PlanNode> result = new LinkedHashMap<>();
        source.forEach((id, value) -> {
            PlanNode copy = new PlanNode(copyIntent(value.getIntent()));
            copy.setId(value.getId());
            copy.setStatus(value.getStatus());
            copy.setReplacesNodeId(value.getReplacesNodeId());
            result.put(id, copy);
        });
        return result;
    }

    private IntentCandidate copyIntent(IntentCandidate source) {
        IntentCandidate copy = new IntentCandidate();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setIntentType(source.getIntentType());
        copy.setDescription(source.getDescription());
        copy.setProgressText(source.getProgressText());
        copy.setExpectedResult(source.getExpectedResult());
        copy.setConfidence(source.getConfidence());
        copy.setPriority(source.getPriority());
        copy.setOriginalOrder(source.getOriginalOrder());
        copy.setDependsOn(safe(source.getDependsOn()));
        copy.setComplexity(source.getComplexity());
        copy.setContextRequirement(source.getContextRequirement());
        return copy;
    }

    private List<String> safe(List<String> values) { return values == null ? List.of() : List.copyOf(values); }
}
