package com.github.agentdock.core.memory;

import java.util.List;

/** 存储与检索均由宿主实现；每次操作严格限定在指定会话。 */
public interface SessionMemoryStore {
    List<SessionMemory> recall(String conversationId, SessionMemoryQuery query);
    void upsert(String conversationId, SessionMemory memory);
    void delete(String conversationId, String key);
}
