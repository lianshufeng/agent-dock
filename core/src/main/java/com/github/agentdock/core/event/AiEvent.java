package com.github.agentdock.core.event;

import java.time.Instant;
import java.util.Map;
import com.github.agentdock.core.type.AiEventType;

import lombok.Data;

@Data
public class AiEvent {
    /** 事件唯一 ID，便于 SSE 客户端去重或断线续传。 */
    private String eventId;
    private String conversationId;
    /** 同一会话中的本次执行 ID。 */
    private String executionId;
    /** 意图级事件对应的意图 ID；会话级事件为空。 */
    private String intentId;
    private AiEventType type;
    /** 面向用户展示的简短状态说明。 */
    private String message;
    /** 当前执行进度，范围为 0 到 100。 */
    private int progress;
    /** 事件扩展数据，不承载核心流程状态。 */
    private Map<String,Object> payload;
    private Instant timestamp;

    public AiEvent() { }
    public AiEvent(String eventId,String conversationId,String executionId,String intentId,AiEventType type,String message,int progress,Map<String,Object> payload,Instant timestamp) {
        this.eventId=eventId;this.conversationId=conversationId;this.executionId=executionId;this.intentId=intentId;this.type=type;this.message=message;this.progress=progress;
        this.payload=payload==null?Map.of():Map.copyOf(payload);this.timestamp=timestamp==null?Instant.now():timestamp;
    }
}
