package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.IntentDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 由宿主注册的意图目录；内核不内置任何行业意图。 */
public final class IntentRegistry {
    private final Map<String, IntentDefinition> definitions = new LinkedHashMap<>();

    public void register(IntentDefinition definition) {
        if (definition == null || definition.getCode() == null || definition.getCode().isBlank()
                || definition.getDescription() == null || definition.getDescription().isBlank()) {
            throw new IllegalArgumentException("意图编码和意图描述不能为空");
        }
        if (definitions.putIfAbsent(definition.getCode(), definition) != null) {
            throw new IllegalArgumentException("重复的意图编码: " + definition.getCode());
        }
    }

    public IntentDefinition find(String code) {
        return definitions.get(code);
    }

    public List<IntentDefinition> definitions() {
        return List.copyOf(definitions.values());
    }
}
