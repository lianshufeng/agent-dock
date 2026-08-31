package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** 单个意图某一轮规划所需的全部通用上下文。 */
@Data
public class IntentLoopRequest {
    private ConversationContext conversation;
    private IntentCandidate intent;
    private List<ConversationMessage> history = List.of();
    private Map<String, IntentResult> previousIntentResults = Map.of();
    private List<AgentObservation> observations = List.of();
    private List<CapabilityDefinition> capabilities = List.of();
    private int iteration;
    /** 工具探索结束后，只允许基于已有真实观察归纳最终结果。 */
    private boolean finalizing;
}
