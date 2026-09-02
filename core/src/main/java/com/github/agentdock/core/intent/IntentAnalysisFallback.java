package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentAdapterResult;

/** LLM 未返回可用候选时的宿主可插拔兜底；内核不包含任何行业关键词。 */
@FunctionalInterface
public interface IntentAnalysisFallback {
    IntentAdapterResult fallback(ConversationContext context, String intentCatalog);
}
