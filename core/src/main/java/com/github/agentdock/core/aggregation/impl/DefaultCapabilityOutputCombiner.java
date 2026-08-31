package com.github.agentdock.core.aggregation.impl;

import com.github.agentdock.core.aggregation.CapabilityOutputCombiner;
import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 不解释输出字段的通用结果组合器。 */
public final class DefaultCapabilityOutputCombiner implements CapabilityOutputCombiner {
    @Override
    public Object combine(IntentCandidate intent, List<Object> outputs) {
        if (outputs == null || outputs.isEmpty()) return null;
        return outputs.size() == 1 ? outputs.get(0) : List.copyOf(outputs);
    }
}
