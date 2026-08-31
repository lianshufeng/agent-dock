package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 从已通过硬准入的能力中选择本轮提供给 LLM 的候选能力。 */
@FunctionalInterface
public interface CapabilityCandidateSelector {
    List<CapabilityDefinition> select(IntentCandidate intent, AiExecutionContext context,
                                      List<CapabilityDefinition> eligibleCapabilities);
}
