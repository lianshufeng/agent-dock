package com.github.agentdock.core.context;

/** 上下文条目的通用来源，业务宿主可通过 BUSINESS_STATE 补充领域事实。 */
public enum ContextSourceType {
    USER_INPUT,
    CONVERSATION_HISTORY,
    ATTACHMENT,
    HOST_ATTRIBUTE,
    EXECUTION_PLAN,
    DEPENDENCY_RESULT,
    TOOL_OBSERVATION,
    FAILURE_RECORD,
    VERIFICATION_EVIDENCE,
    BUSINESS_STATE,
    SESSION_MEMORY,
    SESSION_STATE
}
