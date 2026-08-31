package com.github.agentdock.core.loop;

import com.github.agentdock.core.model.IntentLoopDecision;
import com.github.agentdock.core.model.IntentLoopRequest;

/** 每轮判断意图是否完成，并在未完成时规划下一组能力调用。 */
public interface IntentLoopPlanner {
    IntentLoopDecision decide(IntentLoopRequest request);
}
