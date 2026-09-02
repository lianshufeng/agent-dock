package com.github.agentdock.core.context;

import com.github.agentdock.core.model.*;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 构造某一执行阶段上下文所需的通用输入。 */
@Data
public final class ContextRequest {
    private ContextPhase phase;
    private ConversationContext conversation;
    private IntentCandidate intent;
    private Task task;
    private Map<String, IntentResult> previousIntentResults = Map.of();
    private List<AgentObservation> observations = List.of();
    private List<CapabilityDefinition> capabilities = List.of();
    private TaskVerification verification;
    private Object executionPlan;
}
