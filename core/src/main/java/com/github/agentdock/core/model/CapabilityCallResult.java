package com.github.agentdock.core.model;

/** 统一能力调用结果，同时返回本次消耗的失败恢复预算。 */
public record CapabilityCallResult(CapabilityResult result, int recoveryAttempts) {
}
