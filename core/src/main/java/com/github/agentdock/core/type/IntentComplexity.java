package com.github.agentdock.core.type;

/** 意图复杂度，用于决定采用直接执行还是 Agent 循环。 */
public enum IntentComplexity {
    /** 参数明确、一次能力调用即可完成。 */
    SIMPLE,
    /** 需要规划、多次调用、重试或方案切换。 */
    COMPLEX
}
