package com.github.agentdock.core.loop;

import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 向单意图规划阶段补充宿主或能力域规则。 */
@FunctionalInterface
public interface PlanningRuleContributor {
    List<String> contribute(ConversationContext context, IntentCandidate intent,
                            List<CapabilityDefinition> capabilities);
}
