package com.github.agentdock.core.task;

import com.github.agentdock.core.model.*;

/** 将通用意图转换为任务，不负责选择或调用能力。 */
public interface TaskPlanner {
    Task plan(IntentCandidate intent, AiExecutionContext context);
}
