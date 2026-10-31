package com.github.agentdock.core.planning;

import com.github.agentdock.core.model.ConversationResult;
import com.github.agentdock.core.model.IntentResult;
import java.util.List;
import lombok.Data;

/** 一次执行的可序列化检查点；仅由内核用于恢复。 */
@Data
public final class ExecutionSnapshot {
    private ExecutionPlan plan;
    private List<IntentResult> results = List.of();
    private ConversationResult finalResult;
    /** 已合并补充约束的执行原文；兼容旧检查点缺省。 */
    private String executionInput;
}
