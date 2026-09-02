package com.github.agentdock.core.model;

/** 能力允许的规划自由度。确定性能力不应交给模型自由组合执行。 */
public enum CapabilityExecutionMode {
    DETERMINISTIC,
    GUIDED,
    AGENT
}
