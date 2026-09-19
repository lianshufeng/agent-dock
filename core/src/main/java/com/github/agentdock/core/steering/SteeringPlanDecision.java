package com.github.agentdock.core.steering;

import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 已由核心规范化、可用于生成计划补丁的插入决策。 */
public record SteeringPlanDecision(SteeringAction action, List<String> targetNodeIds,
                                   List<IntentCandidate> intents, String reason,
                                   String clarificationQuestion) {
    public SteeringPlanDecision {
        action = action == null ? SteeringAction.ADD : action;
        targetNodeIds = targetNodeIds == null ? List.of() : List.copyOf(targetNodeIds);
        intents = intents == null ? List.of() : List.copyOf(intents);
    }

    public boolean requiresClarification() {
        return clarificationQuestion != null && !clarificationQuestion.isBlank();
    }
}
