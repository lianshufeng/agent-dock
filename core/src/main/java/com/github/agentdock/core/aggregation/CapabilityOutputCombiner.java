package com.github.agentdock.core.aggregation;

import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 合并同一轮多个终态能力的输出；具体输出结构由宿主决定。 */
@FunctionalInterface
public interface CapabilityOutputCombiner {
    Object combine(IntentCandidate intent, List<Object> outputs);
}
