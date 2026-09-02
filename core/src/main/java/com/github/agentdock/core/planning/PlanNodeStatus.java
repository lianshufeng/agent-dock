package com.github.agentdock.core.planning;

public enum PlanNodeStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    WAITING_USER,
    SKIPPED,
    CANCELLED
}
