package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 能力注册表。由宿主显式注册能力，并校验能力编码唯一性。 */
public class CapabilityRegistry {
    /** 保持注册顺序，便于能力列表稳定输出。 */
    private final Map<String, AiCapability> capabilities = new LinkedHashMap<>();

    public CapabilityRegistry() {
    }

    public CapabilityRegistry(List<? extends AiCapability> capabilities) {
        capabilities.forEach(this::register);
    }

    /** 注册一个能力；同一能力编码只允许注册一次。 */
    public void register(AiCapability capability) {
        if (capability == null || capability.definition() == null) {
            throw new IllegalArgumentException("AI 能力及其定义不能为空");
        }
        String code = capability.definition().getCode();
        if (code == null || code.isBlank() || capabilities.putIfAbsent(code, capability) != null) {
            throw new IllegalArgumentException("重复或无效的 AI 能力编码: " + code);
        }
    }

    /** 按能力编码查找已注册能力。 */
    public AiCapability find(String code) {
        return capabilities.get(code);
    }

    public CapabilityDefinition definition(String code) {
        AiCapability capability = find(code);
        return capability == null ? null : capability.definition();
    }

    public List<CapabilityDefinition> definitions() {
        return capabilities.values().stream().map(AiCapability::definition).toList();
    }
}
