package com.github.agentdock.core.context;

import lombok.Data;

/** 带来源、优先级和预算信息的最小上下文条目。 */
@Data
public final class ContextItem {
    private String id;
    private ContextSourceType sourceType;
    private String sourceId;
    private Object content;
    private int priority;
    private ContextTrustLevel trustLevel;
    private int estimatedTokens;
    private boolean sensitive;
    private long createdAt;

    public ContextItem() { }

    public ContextItem(String id, ContextSourceType sourceType, String sourceId, Object content,
                       int priority, ContextTrustLevel trustLevel, int estimatedTokens,
                       boolean sensitive, long createdAt) {
        this.id = id;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.content = content;
        this.priority = priority;
        this.trustLevel = trustLevel;
        this.estimatedTokens = Math.max(estimatedTokens, 0);
        this.sensitive = sensitive;
        this.createdAt = createdAt;
    }
}
