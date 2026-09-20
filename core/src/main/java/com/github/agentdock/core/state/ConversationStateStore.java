package com.github.agentdock.core.state;

import java.util.Optional;

/** 由宿主实现，所有读写必须按会话隔离。 */
public interface ConversationStateStore {
    Optional<ConversationState> get(String conversationId, String key);
    void put(String conversationId, ConversationState state);
    void delete(String conversationId, String key);
}
