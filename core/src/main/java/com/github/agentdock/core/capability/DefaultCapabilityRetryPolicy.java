package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;

/** 副作用且非幂等的能力不自动重试，其余能力遵守契约和剩余恢复预算。 */
public final class DefaultCapabilityRetryPolicy implements CapabilityRetryPolicy {
    @Override
    public int retries(CapabilityDefinition definition, int remainingRecoveries) {
        if (definition.isSideEffect() && !definition.isIdempotent()) return 0;
        return Math.min(Math.max(definition.getMaxRetries(), 0), Math.max(remainingRecoveries, 0));
    }
}
