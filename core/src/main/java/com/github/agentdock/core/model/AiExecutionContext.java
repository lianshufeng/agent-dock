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

    public AiExecutionContext() {
        this.results = new ConcurrentHashMap<>();
    }

    public AiExecutionContext(ConversationContext conversation) {
        this.conversation = conversation;
        this.results = new ConcurrentHashMap<>();
    }

    private AiExecutionContext(ConversationContext conversation, Map<String, IntentResult> results) {
        this.conversation = conversation;
        this.results = results;
    }

    /** 返回只读结果视图，避免能力实现破坏执行链状态。 */
    public Map<String, IntentResult> getResults() {
        return Collections.unmodifiableMap(results);
    }

    /** 由执行引擎在单个意图完成后写入结果。 */
    public void addResult(IntentResult result) {
        results.put(result.getIntentId(), result);
    }

    /** 为单个意图创建独占的执行上下文，避免并行任务互相覆盖临时状态。 */
    public AiExecutionContext forkForIntent() {
        return new AiExecutionContext(conversation, results);
    }
}
