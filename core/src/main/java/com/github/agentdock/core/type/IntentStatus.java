package com.github.agentdock.core.type;

/** 单个意图或整次会话的处理状态。 */
public enum IntentStatus {
    /** 意图已成功执行并产生结果。 */
    SUCCESS,
    /** 意图执行失败，通常会携带错误信息。 */
    FAILED,
    /** 因前置依赖失败而未执行当前意图。 */
    SKIPPED,
    /** 缺少必要上下文，等待用户补充信息。 */
    WAITING_USER
}
