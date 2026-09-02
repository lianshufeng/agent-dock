package com.github.agentdock.core.model;

/** 任务验收结论对执行器的明确控制动作。 */
public enum VerificationAction {
    PASS,
    RETRY,
    REPLAN,
    WAITING_USER,
    MANUAL_REVIEW,
    FAIL
}
