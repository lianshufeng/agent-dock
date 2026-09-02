package com.github.agentdock.core.model;

/** 统一能力调用结果，同时返回恢复、替代、补偿和耗时信息。 */
public record CapabilityCallResult(CapabilityResult result, int recoveryAttempts,
                                   String actualCapabilityCode, boolean compensationAttempted,
                                   long elapsedMillis) {
    public CapabilityCallResult(CapabilityResult result, int recoveryAttempts) {
        this(result, recoveryAttempts, null, false, 0L);
    }
}
