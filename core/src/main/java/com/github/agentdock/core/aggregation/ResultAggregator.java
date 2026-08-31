package com.github.agentdock.core.aggregation;

import com.github.agentdock.core.model.*;
import java.util.List;

public interface ResultAggregator {
    ConversationResult aggregate(List<IntentResult> results);
}
