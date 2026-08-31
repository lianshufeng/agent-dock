package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 默认候选策略：能力规模较小时将全部合法能力交给 LLM，避免语义预筛选造成召回遗漏。 */
public final class AllEligibleCapabilitySelector implements CapabilityCandidateSelector {
    @Override
    public List<CapabilityDefinition> select(IntentCandidate intent, AiExecutionContext context,
                                             List<CapabilityDefinition> eligibleCapabilities) {
        return eligibleCapabilities == null ? List.of() : List.copyOf(eligibleCapabilities);
    }
}
