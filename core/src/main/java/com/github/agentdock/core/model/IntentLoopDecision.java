package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;

/** 单个意图每轮由 LLM 返回的结构化决策。 */
@Data
public class IntentLoopDecision {
    public enum Status { CONTINUE, COMPLETED, UNRESOLVABLE }
    private Status status;
    private List<CapabilityInvocation> toolInvocations = List.of();
    private String result;
    private String reason;
    /** 仅当本轮全部终态能力成功且已覆盖当前意图目标时直接完成。 */
    private boolean completeAfterTools;
}
