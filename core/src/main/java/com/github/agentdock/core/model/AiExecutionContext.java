package com.github.agentdock.core.model;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import lombok.Data;

/** 在意图之间共享的本次执行上下文；每个意图的临时状态由其本地上下文保存。 */
@Data
public final class AiExecutionContext {
    private ConversationContext conversation;
    private CapabilityInvocation currentCapabilityInvocation;
    private Object currentTaskObservation;
    /** 按意图 ID 保存已完成结果，供后续意图读取依赖输出。 */
    private final Map<String, IntentResult> results;
    /** 按意图 ID 保存最近一次任务验收结论，供计划级重规划读取。 */
    private final Map<String, TaskVerification> verifications;

    public AiExecutionContext() {
        this.results = new ConcurrentHashMap<>();
        this.verifications = new ConcurrentHashMap<>();
    }

    public AiExecutionContext(ConversationContext conversation) {
        this.conversation = conversation;
        this.results = new ConcurrentHashMap<>();
        this.verifications = new ConcurrentHashMap<>();
    }

    private AiExecutionContext(ConversationContext conversation, Map<String, IntentResult> results,
                               Map<String, TaskVerification> verifications) {
        this.conversation = conversation;
        this.results = results;
        this.verifications = verifications;
    }

    /** 返回只读结果视图，避免能力实现破坏执行链状态。 */
    public Map<String, IntentResult> getResults() {
        return Collections.unmodifiableMap(results);
    }

    /** 由执行引擎在单个意图完成后写入结果。 */
    public void addResult(IntentResult result) {
        results.put(result.getIntentId(), result);
    }

    public void removeResult(String intentId) {
        if (intentId != null) results.remove(intentId);
    }

    public Map<String, TaskVerification> getVerifications() {
        return Collections.unmodifiableMap(verifications);
    }

    public void addVerification(String intentId, TaskVerification verification) {
        if (intentId != null && verification != null) verifications.put(intentId, verification);
    }

    /** 为单个意图创建独占的执行上下文，避免并行任务互相覆盖临时状态。 */
    public AiExecutionContext forkForIntent() {
        return new AiExecutionContext(conversation, results, verifications);
    }

    /** 为待决分支节点隔离未选目标，同时保留身份、附件、时间与事件记录。 */
    public AiExecutionContext forkForIntent(String scopedInput) {
        if (scopedInput == null || scopedInput.isBlank()) return forkForIntent();
        ConversationContext scoped = new ConversationContext();
        scoped.setConversationId(conversation.getConversationId());
        scoped.setExecutionId(conversation.getExecutionId());
        scoped.setUserId(conversation.getUserId());
        scoped.setUserInput(scopedInput);
        scoped.setImageUrls(conversation.getImageUrls());
        scoped.setFileIds(conversation.getFileIds());
        scoped.setAttachmentContents(conversation.getAttachmentContents());
        scoped.setHistoryBefore(conversation.getHistoryBefore());
        scoped.setHistory(conversation.getHistory());
        scoped.setAttributes(conversation.getAttributes());
        scoped.setEventPublisher(conversation.getEventPublisher());
        scoped.setModelUsageRecorder(conversation.getModelUsageRecorder());
        return new AiExecutionContext(scoped, results, verifications);
    }
}
