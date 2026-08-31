package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.ConversationContext;

import java.util.List;

/** 向意图分析阶段补充宿主语言或行业规则。 */
@FunctionalInterface
public interface IntentAnalysisRuleContributor {
    List<String> contribute(ConversationContext context);
}
