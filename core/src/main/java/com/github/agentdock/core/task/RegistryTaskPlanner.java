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

    public RegistryTaskPlanner(TaskRegistry registry) {
        this(registry, new DefaultTaskPlanner());
    }

    public RegistryTaskPlanner(TaskRegistry registry, TaskPlanner fallback) {
        this.registry = java.util.Objects.requireNonNull(registry, "任务注册表不能为空");
        this.fallback = fallback == null ? new DefaultTaskPlanner() : fallback;
        this.customPlanner = fallback != null && !(fallback instanceof DefaultTaskPlanner);
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
            if (customPlanner || candidates.size() != 1) return task;
            definition = candidates.get(0);
        }
        task.setDefinitionCode(definition.getCode());
        task.setAllowedCapabilityCodes(definition.getCapabilityCodes());
        // 保留当前请求的目标与领域绑定，只补充注册任务的完成约束。
        task.setSuccessCriteria(task.getSuccessCriteria() == null ? definition.getSuccessCriteria()
                : task.getSuccessCriteria() + "；" + definition.getSuccessCriteria());
        return task;
    }
}
