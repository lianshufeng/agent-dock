package com.github.agentdock.core.task;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.IntentCandidate;
import com.github.agentdock.core.model.Task;
import com.github.agentdock.core.model.TaskDefinition;

/**
 * 将已注册任务定义转换成运行时任务。未命中时回退到兼容的默认任务，
 * 便于宿主逐步迁移而不影响已有意图。
 */
public final class RegistryTaskPlanner implements TaskPlanner {
    private final TaskRegistry registry;
    private final TaskPlanner fallback;
    private final boolean customPlanner;
    private final TaskSelector selector;

    public RegistryTaskPlanner(TaskRegistry registry) {
        this(registry, new DefaultTaskPlanner());
    }

    public RegistryTaskPlanner(TaskRegistry registry, TaskPlanner fallback) {
        this(registry, fallback, null);
    }

    public RegistryTaskPlanner(TaskRegistry registry, TaskPlanner fallback, TaskSelector selector) {
        this.registry = java.util.Objects.requireNonNull(registry, "任务注册表不能为空");
        this.fallback = fallback == null ? new DefaultTaskPlanner() : fallback;
        this.customPlanner = fallback != null && !(fallback instanceof DefaultTaskPlanner);
        this.selector = selector;
    }

    @Override
    public Task plan(IntentCandidate intent, AiExecutionContext context) {
        if (intent == null) throw new IllegalArgumentException("意图不能为空");
        Task task = fallback.plan(intent, context);
        var candidates = registry.findByIntent(intent.getCode());
        // 尚未提供语义选择器时，多候选必须保留原规划路径，不能按注册顺序猜测业务目标。
        TaskDefinition definition;
        if (task.getDefinitionCode() != null) {
            definition = registry.find(task.getDefinitionCode());
            if (definition == null || !definition.supports(intent.getCode()))
                throw new IllegalArgumentException("任务规划器选择了未注册或不适用的任务: " + task.getDefinitionCode());
        } else {
            if (customPlanner || candidates.isEmpty()) return task;
            if (candidates.size() == 1) definition = candidates.get(0);
            else {
                if (selector == null) return task;
                String code = selector.select(intent, context, java.util.List.copyOf(candidates));
                if (code == null || code.isBlank()) return task;
                definition = candidates.stream().filter(item -> code.equals(item.getCode())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("任务选择器返回了候选范围外的任务: " + code));
            }
        }
        task.setDefinitionCode(definition.getCode());
        task.setConfirmationRequired(definition.isConfirmationRequired());
        task.setAllowedCapabilityCodes(definition.getCapabilityCodes());
        task.setSteps(definition.getSteps().stream().map(step -> {
            var copy = new com.github.agentdock.core.model.CapabilityInvocation(step.getCapabilityCode(), step.getArguments());
            copy.setInvocationId(step.getInvocationId());
            copy.setDependsOnInvocationIds(step.getDependsOnInvocationIds() == null ? java.util.List.of()
                    : java.util.List.copyOf(step.getDependsOnInvocationIds()));
            copy.setInputRefs(step.getInputRefs() == null ? java.util.List.of() : step.getInputRefs().stream().map(ref -> {
                var reference = new com.github.agentdock.core.model.IntentInputReference(
                        "$task".equals(ref.getIntentId()) ? intent.getId() : ref.getIntentId(), ref.getPath());
                reference.setArgumentName(ref.getArgumentName());
                reference.setName(ref.getName());
                return reference;
            }).toList());
            return copy;
        }).toList());
        // 保留当前请求的目标与领域绑定，只补充注册任务的完成约束。
        task.setSuccessCriteria(task.getSuccessCriteria() == null ? definition.getSuccessCriteria()
                : task.getSuccessCriteria() + "；" + definition.getSuccessCriteria());
        return task;
    }
}
