package com.github.agentdock.core.execution;

import com.github.agentdock.core.capability.*;
import com.github.agentdock.core.model.*;

public interface DirectExecutor {
    IntentResult execute(IntentCandidate intent, AiExecutionContext context);
}
