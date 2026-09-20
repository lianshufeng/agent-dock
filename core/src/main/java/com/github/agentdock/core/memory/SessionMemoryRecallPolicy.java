package com.github.agentdock.core.memory;

import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentCandidate;
import java.util.List;

/** 宿主决定哪些记忆影响分类，以及每个意图需要哪些记忆。 */
public interface SessionMemoryRecallPolicy {
    default List<SessionMemoryQuery> beforeAnalysis(ConversationContext conversation) { return List.of(); }
    default List<SessionMemoryQuery> forIntent(ConversationContext conversation, IntentCandidate intent) { return List.of(); }
}
