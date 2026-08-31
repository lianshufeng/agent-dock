package com.github.agentdock.core.type;

/** AI 执行过程中对外发布的状态事件类型，可由接口层转发为 SSE。 */
public enum AiEventType {
    /** 开始处理一次会话。 */
    CONVERSATION_STARTED,
    /** 正在分析用户输入中的意图。 */
    INTENT_ANALYZING,
    /** 已识别出一个或多个意图。 */
    INTENT_DETECTED,
    /** 已根据优先级和依赖关系生成串行执行队列。 */
    QUEUE_CREATED,
    /** 开始执行一个意图任务。 */
    TASK_STARTED,
    /** 即将调用某个能力。 */
    TOOL_CALLING,
    /** 能力已返回结果。 */
    TOOL_RESULT,
    /** Agent 正在规划下一步动作。 */
    AGENT_PLANNING,
    /** Agent 正在重试或切换替代方案。 */
    AGENT_RETRYING,
    /** 一个意图任务执行成功。 */
    TASK_SUCCESS,
    /** 一个意图任务执行失败。 */
    TASK_FAILED,
    /** 需要用户补充信息后才能继续。 */
    WAITING_USER,
    /** 所有意图处理完成并生成统一结果。 */
    FINAL_RESULT,
    HISTORY_LOADED,
    AGENT_DECISION,
    UI_RESULT,
    EXECUTION_FINISHED,
    MODEL_USAGE,
    INTENT_METRICS,
    INPUT_BOUND,
    PIPELINE_BRANCH
    ,TASK_CREATED
    ,TASK_VERIFIED
    ,TASK_REPLANNED
}
