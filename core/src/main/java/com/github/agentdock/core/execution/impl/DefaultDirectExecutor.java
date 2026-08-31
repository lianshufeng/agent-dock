package com.github.agentdock.core.execution.impl;

import com.github.agentdock.core.capability.*;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.execution.DirectExecutor;
public class DefaultDirectExecutor implements DirectExecutor {
    private final CapabilityRegistry registry;

    public DefaultDirectExecutor(CapabilityRegistry registry) { this.registry = registry; }

    @Override
    public IntentResult execute(IntentCandidate intent, AiExecutionContext context) {
        CapabilityInvocation invocation = intent.getCapabilityInvocation();
        if (invocation == null || invocation.getCapabilityCode() == null || invocation.getCapabilityCode().isBlank()) {
            return IntentResult.failed(intent, "意图未指定执行能力");
        }
        AiCapability capability = registry.find(invocation.getCapabilityCode());
        if (capability == null) return IntentResult.failed(intent, "未注册 AI 能力: " + invocation.getCapabilityCode());
        try {
            CapabilityResult result = capability.invoke(intent, context);
            return result.isSuccess() ? IntentResult.success(intent, result.getOutput()) : IntentResult.failed(intent, result.getMessage());
        } catch (RuntimeException ex) {
            return IntentResult.failed(intent, ex.getMessage() == null ? "能力执行失败" : ex.getMessage());
        }
    }
}
