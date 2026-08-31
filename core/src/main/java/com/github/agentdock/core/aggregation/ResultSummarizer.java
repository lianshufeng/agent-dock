package com.github.agentdock.core.aggregation;

import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.ConversationResult;

/** 将结构化聚合结果转换为最终面向用户的回答。 */
public interface ResultSummarizer {
    String summarize(ConversationContext context, ConversationResult result);
}
