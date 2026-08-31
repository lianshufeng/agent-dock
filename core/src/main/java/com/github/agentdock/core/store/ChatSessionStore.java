package com.github.agentdock.core.store;

import java.util.List;

/** 会话及消息的完整存储接口；AI 内核只依赖其父接口。 */
public interface ChatSessionStore extends ChatHistoryStore {
    String createSession(String ownerKey, String title);
    List<ChatSessionSummary> listSession(String ownerKey, String keyword, int page, int size);
    boolean removeSession(String sessionId, String ownerKey);
    record ChatSessionSummary(String id, String title, long messageCount, long lastMessageAt) {}
}
