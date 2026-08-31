package com.github.agentdock.core.type;

/** 意图执行路径，由执行路由器根据意图复杂度选择。 */
public enum ExecutionRoute {
    /** 简单意图，直接调用已注册能力。 */
    DIRECT,
    /** 有前置意图依赖或显式输入绑定的串行管线。 */
    PIPELINE,
    /** 复杂意图，交给 Agent Loop 进行规划、调用和观察。 */
    AGENT_LOOP
}
