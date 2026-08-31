package com.github.agentdock.core.provider;

import com.github.agentdock.core.model.ModelUsageRecord;

/** AI 内核向宿主报告模型用量的可插拔回调。 */
@FunctionalInterface
public interface ModelUsageRecorder {
    void record(ModelUsageRecord usage);
}
