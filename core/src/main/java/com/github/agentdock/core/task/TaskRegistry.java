package com.github.agentdock.core.task;

import com.github.agentdock.core.model.TaskDefinition;
import com.github.agentdock.core.capability.CapabilityRegistry;
import com.github.agentdock.core.intent.IntentRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 宿主注册的任务目录；内核只负责校验和稳定查找，不包含领域任务。 */
public final class TaskRegistry {
    private final Map<String, TaskDefinition> definitions = new LinkedHashMap<>();

    public void register(TaskDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("任务定义不能为空");
        definition.validate();
        if (definitions.putIfAbsent(definition.getCode(), definition) != null)
            throw new IllegalArgumentException("重复的任务编码: " + definition.getCode());
    }

    public TaskDefinition find(String code) { return definitions.get(code); }

    public TaskDefinition require(String code) {
        TaskDefinition definition = find(code);
        if (definition == null) throw new IllegalArgumentException("未注册任务: " + code);
        return definition;
    }

    public void validateTaskInput(TaskDefinition definition, Map<String, Object> input) {
        var contract = new com.github.agentdock.core.model.CapabilityDefinition();
        contract.setInputSchema(definition.getInputSchema());
        contract.setInputContract(definition.getInputContract());
        String issue = new com.github.agentdock.core.capability.CapabilityInvocationValidator().validate(contract, input);
        if (issue != null) throw new IllegalArgumentException("任务输入无效：" + issue);
    }

    public List<TaskDefinition> definitions() { return List.copyOf(definitions.values()); }

    public List<TaskDefinition> findByIntent(String intentCode) {
        return definitions.values().stream().filter(item -> item.supports(intentCode)).toList();
    }

    public boolean isEmpty() { return definitions.isEmpty(); }

    /** 在内核开始执行前校验任务引用，避免宿主配置顺序影响启动。 */
    public void validateReferences(IntentRegistry intents, CapabilityRegistry capabilities) {
        for (TaskDefinition task : definitions.values()) {
            task.validate();
            for (String intentCode : task.getIntentCodes()) {
                if (intents.find(intentCode) == null)
                    throw new IllegalStateException("任务引用了未注册意图: " + task.getCode() + " -> " + intentCode);
            }
            for (String capabilityCode : task.getCapabilityCodes()) {
                if (capabilities.find(capabilityCode) == null)
                    throw new IllegalStateException("任务引用了未注册能力: " + task.getCode() + " -> " + capabilityCode);
            }
        }
    }
}
