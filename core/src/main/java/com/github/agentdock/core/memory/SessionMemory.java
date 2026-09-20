package com.github.agentdock.core.memory;

/** 属于单个会话的结构化事实或状态。 */
public record SessionMemory(String key, Object value, long updatedAt) {
    public SessionMemory {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("记忆键不能为空");
    }
}
