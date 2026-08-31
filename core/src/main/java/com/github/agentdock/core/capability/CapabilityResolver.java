package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 根据宿主硬准入策略得到当前上下文允许使用的能力目录。 */
public interface CapabilityResolver {
    List<CapabilityDefinition> resolve(IntentCandidate intent, AiExecutionContext context, CapabilityRegistry registry);
}
