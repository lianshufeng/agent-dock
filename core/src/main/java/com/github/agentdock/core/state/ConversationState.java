package com.github.agentdock.core.state;

/** 当前会话中的结构化状态。 */
public record ConversationState(String key, Object value, long updatedAt) {
    public ConversationState {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("状态键不能为空");
    }
}
