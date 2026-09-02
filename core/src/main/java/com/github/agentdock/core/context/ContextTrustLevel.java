package com.github.agentdock.core.context;

/** 上下文可信级别；外部文本永远不能提升为系统规则。 */
public enum ContextTrustLevel {
    SYSTEM,
    HOST_VERIFIED,
    TOOL_VERIFIED,
    USER_PROVIDED,
    EXTERNAL_UNTRUSTED
}
