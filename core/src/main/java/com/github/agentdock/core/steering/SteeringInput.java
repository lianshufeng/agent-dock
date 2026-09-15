package com.github.agentdock.core.steering;

import java.util.Map;

/** 执行过程中由宿主投递的一条用户补充输入。 */
public record SteeringInput(String inputId, String content, long receivedAt, Map<String, Object> metadata) {
    public SteeringInput {
        if (inputId == null || inputId.isBlank()) throw new IllegalArgumentException("插入输入标识不能为空");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("插入输入内容不能为空");
        content = content.trim();
        receivedAt = receivedAt <= 0 ? System.currentTimeMillis() : receivedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
