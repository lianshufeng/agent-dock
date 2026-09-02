package com.github.agentdock.core.model;

import com.github.agentdock.core.context.ContextSnapshot;
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
    /** 由统一上下文工程生成，模型适配器应优先读取本快照。 */
    private ContextSnapshot contextSnapshot = ContextSnapshot.EMPTY;
    /** 工具探索结束后，只允许基于已有真实观察归纳最终结果。 */
    private boolean finalizing;
}
