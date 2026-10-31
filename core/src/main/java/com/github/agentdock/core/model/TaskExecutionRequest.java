package com.github.agentdock.core.model;

import java.util.Map;

/** 受宿主权限保护的结构化任务请求；不允许通过会话 attributes 注入任务编码。 */
public record TaskExecutionRequest(String taskCode, String intentCode, Map<String, Object> inputs) {
    public TaskExecutionRequest {
        if (taskCode == null || taskCode.isBlank()) throw new IllegalArgumentException("任务编码不能为空");
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}
