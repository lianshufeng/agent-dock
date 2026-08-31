package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;

/** 统一决定能力调用允许的自动重试次数。 */
public interface CapabilityRetryPolicy {
    int retries(CapabilityDefinition definition, int remainingRecoveries);
}
