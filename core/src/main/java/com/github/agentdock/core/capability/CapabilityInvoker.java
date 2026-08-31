package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;

/** 工具调用边界，统一负责能力范围校验、参数校验、超时和有限恢复。 */
public interface CapabilityInvoker {
    CapabilityCallResult invoke(IntentCandidate intent, AiExecutionContext context,
                                CapabilityInvocation invocation, int remainingRecoveries);
}
