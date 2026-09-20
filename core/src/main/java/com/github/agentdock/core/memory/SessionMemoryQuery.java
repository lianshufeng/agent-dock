package com.github.agentdock.core.memory;

/** 精确键查询或由宿主实现的内容检索。 */
public record SessionMemoryQuery(String key, String text, int limit) {
    public SessionMemoryQuery {
        if ((key == null || key.isBlank()) == (text == null || text.isBlank()))
            throw new IllegalArgumentException("必须指定记忆键或检索文本之一");
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("记忆召回数量必须在 1 到 20 之间");
    }

    public static SessionMemoryQuery key(String key) { return new SessionMemoryQuery(key, null, 1); }
    public static SessionMemoryQuery search(String text, int limit) { return new SessionMemoryQuery(null, text, limit); }
}
