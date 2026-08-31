package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentCandidate;
import java.util.List;

/** 宿主可插拔的上下文召回兜底策略；不得生成意图或选择能力。 */
@FunctionalInterface
public interface ContextRecallPolicy {
    void apply(ConversationContext context, List<IntentCandidate> candidates);

    static ContextRecallPolicy noop() {
        return (context, candidates) -> { };
    }
}
