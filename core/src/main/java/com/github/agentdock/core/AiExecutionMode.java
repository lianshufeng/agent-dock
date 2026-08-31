package com.github.agentdock.core;

/** 单次 AI 执行的调度模式；默认串行以兼容未声明并发能力的下游。 */
public enum AiExecutionMode {
    SERIAL,
    PARALLEL
}
