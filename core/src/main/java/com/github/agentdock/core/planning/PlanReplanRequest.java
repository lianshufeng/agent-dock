package com.github.agentdock.core.planning;

import com.github.agentdock.core.context.ContextSnapshot;
import com.github.agentdock.core.model.*;
import lombok.Data;

import java.util.List;

/** 计划重规划只接收当前计划、失败证据和已注册意图，不接触宿主容器。 */
@Data
public final class PlanReplanRequest {
    private ExecutionPlan plan;
    private PlanNode failedNode;
    private IntentResult result;
    private TaskVerification verification;
    private ContextSnapshot contextSnapshot = ContextSnapshot.EMPTY;
    private List<IntentDefinition> allowedIntents = List.of();
    private ConversationContext conversation;
}
