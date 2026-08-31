package com.github.agentdock.core.store;

import com.github.agentdock.core.model.ConversationMessage;
import java.util.List;

/** AI 内核使用的最小聊天历史存储接口，由业务扩展实现。 */
public interface ChatHistoryStore {
    ConversationMessage append(String sessionId, String role, String message);
    List<ConversationMessage> load(String sessionId, int limit);
    default ConversationMessage append(String sessionId, ConversationMessage message) {
        return append(sessionId, message.getRole(), message.getContent());
    }
    default List<ConversationMessage> loadBefore(String sessionId, int limit, long before) {
        return load(sessionId, limit).stream().filter(message -> message.getTimestamp() < before).toList();
    }
}
