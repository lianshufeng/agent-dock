package com.github.agentdock.core.model;

import lombok.Data;

/** 一次模型通信的通用用量事实；不包含任何业务存储或计费逻辑。 */
@Data
public class ModelUsageRecord {
    private String usageId;
    private String conversationId;
    private String executionId;
    private String intentId;
    private String stage;
    private int iteration;
    private String provider;
    private String modelName;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;
    private boolean usageAvailable;
    private boolean success;
    private long elapsedMillis;
    private long occurredAt;
}
