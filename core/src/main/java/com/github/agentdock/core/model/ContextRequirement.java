package com.github.agentdock.core.model;

import java.util.LinkedHashSet;
import java.util.Set;

/** 意图分析阶段给出的上下文召回要求。 */
import lombok.Data;

@Data
public class ContextRequirement {
    /** 是否需要召回当前会话的历史消息。 */
    private boolean recallHistory;
    /** 建议召回的最大历史消息条数，0 表示不召回。 */
    private int historyLimit;
    /** 除对话历史外，需要宿主补充的上下文作用域标识。 */
    private Set<String> scopes;

    public static final ContextRequirement NONE = new ContextRequirement(false, 0, Set.of());

    public ContextRequirement() { this(false, 0, Set.of()); }
    public ContextRequirement(boolean recallHistory, int historyLimit, Set<String> scopes) {
        this.recallHistory = recallHistory; this.historyLimit = Math.max(historyLimit, 0); this.scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    /** 合并多个意图的上下文要求，历史条数取最大值，作用域取并集。 */
    public ContextRequirement merge(ContextRequirement other) {
        if (other == null) return this;
        Set<String> mergedScopes = new LinkedHashSet<>(scopes);
        mergedScopes.addAll(other.scopes);
        return new ContextRequirement(recallHistory || other.recallHistory,
                Math.max(historyLimit, other.historyLimit), mergedScopes);
    }
}
