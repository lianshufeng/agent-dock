package com.github.agentdock.core.task;

import com.github.agentdock.core.model.*;

/** 兼容迁移阶段的默认任务规划器：每个意图先生成一个独立任务。 */
public final class DefaultTaskPlanner implements TaskPlanner {
    @Override
    public Task plan(IntentCandidate intent, AiExecutionContext context) {
        return new Task("task-" + intent.getId(), intent.getId(), intent.getDescription(),
                intent.getExpectedResult(), intent.getDependsOn());
    }
}
