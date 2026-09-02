package com.github.agentdock.core.context;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 已完成裁剪、可直接交给模型或验证器的不可变上下文快照。 */
@Data
public final class ContextSnapshot {
    public static final ContextSnapshot EMPTY = new ContextSnapshot(null, List.of(), 0, 0);
    private final ContextPhase phase;
    private final List<ContextItem> items;
    private final int estimatedTokens;
    private final int truncatedItems;

    public ContextSnapshot(ContextPhase phase, List<ContextItem> items, int estimatedTokens, int truncatedItems) {
        this.phase = phase;
        this.items = items == null ? List.of() : List.copyOf(items);
        this.estimatedTokens = Math.max(estimatedTokens, 0);
        this.truncatedItems = Math.max(truncatedItems, 0);
    }

    /** 生成稳定、有来源标记的提示词对象，避免不同供应商各自拼装上下文。 */
    public List<Map<String, Object>> promptItems() {
        return items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("sourceType", item.getSourceType());
            value.put("sourceId", item.getSourceId());
            value.put("trustLevel", item.getTrustLevel());
            value.put("content", item.getContent());
            return value;
        }).toList();
    }
}
