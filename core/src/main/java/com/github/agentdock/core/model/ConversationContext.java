package com.github.agentdock.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.agentdock.core.event.AiEventPublisher;
import com.github.agentdock.core.provider.ModelUsageRecorder;
import com.github.agentdock.core.provider.NoopModelUsageRecorder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** AI 内核的一次会话输入。 */
@Data
public class ConversationContext {
    private String conversationId;
    private String executionId;
    private String userId;
    private String userInput;
    /** 本次输入携带的图片 URL；图片内容由模型供应商按 URL 获取。 */
    private List<String> imageUrls = List.of();
    private List<String> fileIds = List.of();
    private List<AttachmentContent> attachmentContents = List.of();
    private long historyBefore;
    private List<ConversationMessage> history = List.of();
    private Map<String, Object> attributes = Map.of();
    @JsonIgnore
    private AiEventPublisher eventPublisher = event -> { };
    @JsonIgnore
    private ModelUsageRecorder modelUsageRecorder = NoopModelUsageRecorder.INSTANCE;
    public ConversationContext() { }
    public ConversationContext(String conversationId, String executionId, String userId, String userInput,
                               List<ConversationMessage> history, Map<String, Object> attributes) {
        this.conversationId = conversationId; this.executionId = executionId; this.userId = userId; this.userInput = userInput;
        this.history = history == null ? List.of() : List.copyOf(history); this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
