package com.github.agentdock.core.context;

/** 模型或验证器请求上下文时所处的执行阶段。 */
public enum ContextPhase {
    INTENT_ANALYSIS,
    TASK_PLANNING,
    TASK_REPLANNING,
    CAPABILITY_BINDING,
    RESULT_VERIFICATION,
    RESULT_SUMMARY
}
