package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentAdapterResult;

/** 宿主可插拔的意图分析后处理；通用内核不包含行业规则。 */
@FunctionalInterface
public interface IntentAnalysisPostProcessor {
    IntentAdapterResult process(ConversationContext context, IntentAdapterResult analysis);
}
