package com.github.agentdock.core.model;

/** 统一能力调用结果；未知副作用核实独立于失败重试预算并通过专用标记暴露。 */
public record CapabilityCallResult(CapabilityResult result, int recoveryAttempts,
                                   String actualCapabilityCode, boolean compensationAttempted,
                                   long elapsedMillis, boolean unknownOutcomeRecoveryAttempted,
                                   boolean unknownOutcomeRecovered) {
    public CapabilityCallResult(CapabilityResult result, int recoveryAttempts) {
        this(result, recoveryAttempts, null, false, 0L, false, false);
    }

    /** Backwards compatible constructor for callers that predate explicit unknown-outcome recovery metadata. */
    public CapabilityCallResult(CapabilityResult result, int recoveryAttempts, String actualCapabilityCode,
                                boolean compensationAttempted, long elapsedMillis) {
        this(result, recoveryAttempts, actualCapabilityCode, compensationAttempted, elapsedMillis, false, false);
    }
}
