package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.IntentCandidate;

/** 按权限、业务范围或设备状态限制工具可见性，不负责意图与工具的静态映射。 */
@FunctionalInterface
public interface CapabilityVisibilityPolicy {
    boolean isVisible(IntentCandidate intent, CapabilityDefinition capability, AiExecutionContext context);
}
