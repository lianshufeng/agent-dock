package com.github.agentdock.core.agent;

import com.github.agentdock.core.model.*;

/** 复杂意图的有限循环执行器，由具体项目提供规划器实现。 */
public interface AgentLoop {
    IntentResult execute(IntentCandidate intent, AiExecutionContext context);
}
