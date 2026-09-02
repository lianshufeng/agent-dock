package com.github.agentdock.core.context;

/** 单次模型或验证调用可使用的上下文预算。 */
public record ContextBudget(int maxTokens, int maxItems, int maxItemCharacters) {
    public ContextBudget {
        maxTokens = Math.max(maxTokens, 256);
        maxItems = Math.max(maxItems, 1);
        maxItemCharacters = Math.max(maxItemCharacters, 256);
    }

    public static ContextBudget defaults(ContextPhase phase) {
        return switch (phase) {
            case INTENT_ANALYSIS -> new ContextBudget(8_000, 32, 8_000);
            case TASK_PLANNING, TASK_REPLANNING -> new ContextBudget(16_000, 64, 16_000);
            case CAPABILITY_BINDING -> new ContextBudget(8_000, 32, 8_000);
            case RESULT_VERIFICATION -> new ContextBudget(12_000, 48, 12_000);
            case RESULT_SUMMARY -> new ContextBudget(20_000, 64, 20_000);
        };
    }
}
