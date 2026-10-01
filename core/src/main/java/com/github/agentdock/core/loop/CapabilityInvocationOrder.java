package com.github.agentdock.core.loop;

import com.github.agentdock.core.model.CapabilityInvocation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按显式 invocationId 依赖排序；无依赖调用保持输入顺序。 */
final class CapabilityInvocationOrder {
    private CapabilityInvocationOrder() { }

    static List<CapabilityInvocation> order(List<CapabilityInvocation> invocations) {
        if (invocations == null || invocations.size() < 2) {
            return invocations == null ? List.of() : invocations;
        }
        Map<String, CapabilityInvocation> byId = new LinkedHashMap<>();
        for (CapabilityInvocation invocation : invocations) {
            if (invocation != null && invocation.getInvocationId() != null && !invocation.getInvocationId().isBlank()) {
                if (byId.put(invocation.getInvocationId(), invocation) != null) {
                    throw new IllegalArgumentException("工具调用 ID 重复");
                }
            }
        }
        if (byId.isEmpty()) return invocations;
        List<CapabilityInvocation> result = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (CapabilityInvocation invocation : invocations) {
            visit(invocation, byId, visiting, visited, result);
        }
        return result;
    }

    private static void visit(CapabilityInvocation invocation, Map<String, CapabilityInvocation> byId,
                              Set<String> visiting, Set<String> visited, List<CapabilityInvocation> result) {
        String id = invocation == null ? null : invocation.getInvocationId();
        if (id == null || id.isBlank()) {
            result.add(invocation);
            return;
        }
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("工具调用依赖存在环: " + id);
        List<String> dependencies = invocation.getDependsOnInvocationIds() == null
                ? List.of() : invocation.getDependsOnInvocationIds();
        for (String dependency : dependencies) {
            CapabilityInvocation parent = byId.get(dependency);
            if (parent == null) throw new IllegalArgumentException("工具调用依赖不存在: " + dependency);
            visit(parent, byId, visiting, visited, result);
        }
        visiting.remove(id);
        visited.add(id);
        result.add(invocation);
    }
}
