package com.github.agentdock.core.steering;

/** 补充输入的计划处理策略；旧覆盖策略仅用于紧急回退。 */
public enum SteeringPlanMode {
    CONSERVATIVE,
    LEGACY_REPLACE_PENDING
}
