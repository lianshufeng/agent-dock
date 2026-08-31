package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.IntentCandidate;

/** 默认允许已注册能力进入搜索目录，宿主可注册更严格的策略。 */
public final class AllowAllCapabilityVisibilityPolicy implements CapabilityVisibilityPolicy {
    @Override
    public boolean isVisible(IntentCandidate intent, CapabilityDefinition capability, AiExecutionContext context) {
        return capability != null;
    }
}
