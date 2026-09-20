package com.github.agentdock.core.state;

import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentCandidate;
import java.util.List;

/** 宿主声明哪些状态会影响意图分析和任务规划。 */
public interface ConversationStatePolicy {
    default List<String> beforeAnalysis(ConversationContext conversation) { return List.of(); }
    default List<String> forIntent(ConversationContext conversation, IntentCandidate intent) { return List.of(); }
}
