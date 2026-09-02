package com.github.agentdock.core.context;

/** 宿主可覆盖不同阶段的上下文预算。 */
@FunctionalInterface
public interface ContextPolicy {
    ContextBudget budget(ContextRequest request);

    static ContextPolicy defaults() {
        return request -> ContextBudget.defaults(request == null || request.getPhase() == null
                ? ContextPhase.TASK_PLANNING : request.getPhase());
    }
}
