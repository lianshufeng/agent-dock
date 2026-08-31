package com.github.agentdock.core.model;

/** 与具体大模型无关的会话消息。 */
import lombok.Data;

@Data
public class ConversationMessage {
    private String role;
    private String content;
    private long timestamp;
    private String executionId;
    private String idempotencyKey;
    private java.util.List<String> imageUrls = java.util.List.of();
    private java.util.List<String> fileIds = java.util.List.of();
    public ConversationMessage() { }
    public ConversationMessage(String role, String content, long timestamp) { this.role = role; this.content = content; this.timestamp = timestamp; }
}
