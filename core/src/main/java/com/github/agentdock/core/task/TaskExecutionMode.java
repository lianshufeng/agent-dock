package com.github.agentdock.core.task;

/** 任务的执行方式；固定流程与开放式规划由内核分别处理。 */
public enum TaskExecutionMode {
    DIRECT,
    WORKFLOW,
    AGENT
}
