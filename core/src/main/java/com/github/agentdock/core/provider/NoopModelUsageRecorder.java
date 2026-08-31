package com.github.agentdock.core.provider;

import com.github.agentdock.core.model.ModelUsageRecord;

public enum NoopModelUsageRecorder implements ModelUsageRecorder {
    INSTANCE;
    @Override public void record(ModelUsageRecord usage) { }
}
