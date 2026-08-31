package com.github.agentdock.core.routing;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.ExecutionRoute;

public interface ExecutionRouter {
    ExecutionRoute route(IntentCandidate intent);
}
