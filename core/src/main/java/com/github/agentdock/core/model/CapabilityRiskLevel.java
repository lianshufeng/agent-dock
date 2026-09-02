package com.github.agentdock.core.model;

/** 能力副作用风险，由宿主声明，核心据此决定默认执行约束。 */
public enum CapabilityRiskLevel {
    READ_ONLY,
    REVERSIBLE_WRITE,
    DESTRUCTIVE_WRITE
}
