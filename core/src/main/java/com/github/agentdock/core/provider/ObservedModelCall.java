package com.github.agentdock.core.provider;

import com.github.agentdock.core.event.AiEvent;
import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.ModelUsageRecord;
import com.github.agentdock.core.type.AiEventType;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 记录模型真实返回的 Token 用量和请求耗时。 */
public final class ObservedModelCall {
    private ObservedModelCall() { }
    public static ChatResponse chat(ChatModel model, ChatRequest request, ConversationContext context,
                                    String intentId, String stage, int iteration) {
        long start = System.nanoTime();
        ChatResponse response = null;
        try {
            response = model.chat(request);
            return response;
        } finally {
            String usageId = UUID.randomUUID().toString();
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("usageId", usageId);
            metrics.put("stage", stage);
            metrics.put("iteration", iteration);
            metrics.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000);
            metrics.put("success", response != null);
            metrics.put("requestCharacters", request.messages().toString().length());
            var usage = response == null ? null : response.tokenUsage();
            metrics.put("usageAvailable", usage != null);
            if (usage != null) {
                metrics.put("inputTokens", usage.inputTokenCount());
                metrics.put("outputTokens", usage.outputTokenCount());
                metrics.put("totalTokens", usage.totalTokenCount());
            }
            if (response != null && response.modelName() != null) metrics.put("modelName", response.modelName());
            if (context != null) {
                try {
                    ModelUsageRecord record = new ModelUsageRecord();
                    record.setUsageId(usageId);
                    record.setConversationId(context.getConversationId());
                    record.setExecutionId(context.getExecutionId());
                    record.setIntentId(intentId);
                    record.setStage(stage);
                    record.setIteration(iteration);
                    record.setModelName(response == null ? null : response.modelName());
                    record.setInputTokens(usage == null ? null : usage.inputTokenCount());
                    record.setOutputTokens(usage == null ? null : usage.outputTokenCount());
                    record.setTotalTokens(usage == null ? null : usage.totalTokenCount());
                    record.setUsageAvailable(usage != null);
                    record.setSuccess(response != null);
                    record.setElapsedMillis((System.nanoTime() - start) / 1_000_000);
                    record.setOccurredAt(System.currentTimeMillis());
                    context.getModelUsageRecorder().record(record);
                } catch (RuntimeException exception) {
                    org.slf4j.LoggerFactory.getLogger(ObservedModelCall.class).warn("模型用量回调失败", exception);
                }
                try {
                    context.getEventPublisher().publish(new AiEvent(UUID.randomUUID().toString(),
                            context.getConversationId(), context.getExecutionId(), intentId,
                            AiEventType.MODEL_USAGE, stageMessage(stage), stageProgress(stage), metrics, null));
                } catch (RuntimeException exception) {
                    org.slf4j.LoggerFactory.getLogger(ObservedModelCall.class).warn("模型用量记录失败", exception);
                }
            }
        }
    }

    private static String stageMessage(String stage) {
        return switch (stage == null ? "" : stage) {
            case "intent-analysis" -> "已完成需求理解";
            case "intent-loop" -> "已完成执行方案规划";
            case "result-summary" -> "已完成结果整理";
            default -> "已完成一次模型处理";
        };
    }


    private static int stageProgress(String stage) {
        return "intent-analysis".equals(stage) ? 8 : "intent-loop".equals(stage) ? 42
                : "result-summary".equals(stage) ? 90 : 5;
    }
}
