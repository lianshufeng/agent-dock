package com.github.agentdock.core.planning;

import com.github.agentdock.core.model.IntentCandidate;
import lombok.Data;

/** 执行计划节点；首版一对一包装意图，后续可扩展为更细任务节点。 */
@Data
public final class PlanNode {
    private String id;
    private IntentCandidate intent;
    private PlanNodeStatus status = PlanNodeStatus.PENDING;
    private String replacesNodeId;

    public PlanNode() { }

    public PlanNode(IntentCandidate intent) {
        this.id = intent == null ? null : intent.getId();
        this.intent = intent;
    }
}
