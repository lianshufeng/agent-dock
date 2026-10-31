package com.github.agentdock.core.task;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.IntentCandidate;
import com.github.agentdock.core.model.TaskDefinition;
import java.util.List;

/** 在当前意图的任务候选内选择任务；空结果表示保留原规划路径。 */
@FunctionalInterface
public interface TaskSelector {
    String select(IntentCandidate intent, AiExecutionContext context, List<TaskDefinition> candidates);
}
