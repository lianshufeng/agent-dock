package com.github.agentdock.core.routing.impl;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.*;
import com.github.agentdock.core.routing.ExecutionRouter;
public class DefaultExecutionRouter implements ExecutionRouter {
    @Override
    public ExecutionRoute route(IntentCandidate intent) {
        if (intent.getDependsOn() != null && !intent.getDependsOn().isEmpty()) return ExecutionRoute.PIPELINE;
        // 当前主执行器统一经过任务规划/有限 Loop；仅显式绑定能力的兼容请求才走直接能力调用。
        return intent.getCapabilityInvocation() != null ? ExecutionRoute.DIRECT : ExecutionRoute.AGENT_LOOP;
    }
}
