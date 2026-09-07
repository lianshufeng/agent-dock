package com.github.agentdock.core.capability;

import com.github.agentdock.core.internal.ExecutorSupport;
import com.github.agentdock.core.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/** 基于 JDK 的默认调用器；宿主可以注册其他线程模型的实现。 */
public final class DefaultCapabilityInvoker implements CapabilityInvoker {
    /** 能力未提供资源键前，对副作用调用保守互斥；只读能力仍可并行。 */
    private static final java.util.concurrent.locks.ReentrantLock SIDE_EFFECT_LOCK =
            new java.util.concurrent.locks.ReentrantLock(true);
    private static final int CPU_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int CAPABILITY_PARALLELISM = Math.max(1, CPU_COUNT / 4);
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            CAPABILITY_PARALLELISM, CAPABILITY_PARALLELISM, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(8, CAPABILITY_PARALLELISM * 4)),
            ExecutorSupport.daemonThreadFactory("ai-capability-invoker"),
            new ThreadPoolExecutor.AbortPolicy());

    public void shutdown() {
        EXECUTOR.shutdownNow();
    }

    public static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
    }

    private final CapabilityRegistry registry;
    private final CapabilityVisibilityPolicy visibilityPolicy;
    private final CapabilityInvocationValidator invocationValidator = new CapabilityInvocationValidator();
    private final CapabilityRetryPolicy retryPolicy = new DefaultCapabilityRetryPolicy();

    public DefaultCapabilityInvoker(CapabilityRegistry registry) {
        this(registry, new AllowAllCapabilityVisibilityPolicy());
    }

    public DefaultCapabilityInvoker(CapabilityRegistry registry, CapabilityVisibilityPolicy visibilityPolicy) {
        this.registry = registry;
        this.visibilityPolicy = visibilityPolicy == null ? new AllowAllCapabilityVisibilityPolicy() : visibilityPolicy;
    }

    @Override
    public CapabilityCallResult invoke(IntentCandidate intent, AiExecutionContext context,
                                       CapabilityInvocation invocation, int remainingRecoveries) {
        long startedAt = System.nanoTime();
        AiCapability capability = registry.find(invocation.getCapabilityCode());
        if (capability == null) return failure("CAPABILITY_NOT_REGISTERED", "未注册能力: " + invocation.getCapabilityCode(), 0,
                invocation.getCapabilityCode(), startedAt);
        CapabilityDefinition definition = capability.definition();
        if (!visibilityPolicy.isVisible(intent, definition, context)) {
            return failure("CAPABILITY_NOT_ALLOWED", "当前意图不允许调用能力: " + invocation.getCapabilityCode(), 0,
                    invocation.getCapabilityCode(), startedAt);
        }
        String validationMessage = invocationValidator.validate(definition, invocation.getArguments());
        if (validationMessage != null) return failure("INVALID_ARGUMENTS", validationMessage, 0,
                invocation.getCapabilityCode(), startedAt);
        boolean locked = definition.isSideEffect();
        if (locked) SIDE_EFFECT_LOCK.lock();
        try {

        int recoveries = 0;
        boolean compensationAttempted = false;
        String actualCapabilityCode = invocation.getCapabilityCode();
        int retries = retryPolicy.retries(definition, remainingRecoveries);
        CapabilityResult result;
        do {
            context.setCurrentCapabilityInvocation(invocation);
            result = invokeOnce(capability, intent, context, definition.getTimeout());
            result = validateOutput(definition, result);
            if (result.isSuccess() || !result.isRetryable() || recoveries >= retries) break;
            recoveries++;
        } while (true);

        if (!result.isSuccess() && definition.isSideEffect() && definition.isCompensatable()
                && recoveries < remainingRecoveries) {
            recoveries++;
            compensationAttempted = true;
            CapabilityResult compensation = compensateOnce(capability, intent, context, invocation, result,
                    definition.getTimeout());
            if (!compensation.isSuccess()) {
                result = CapabilityResult.failure("COMPENSATION_FAILED", compensation.getMessage(), false);
            }
        }

        if (!result.isSuccess() && recoveries < remainingRecoveries) {
            for (String fallbackCode : safe(definition.getFallbackCapabilities())) {
                AiCapability fallback = registry.find(fallbackCode);
                if (fallback == null || !visibilityPolicy.isVisible(intent, fallback.definition(), context)) continue;
                String fallbackValidation = invocationValidator.validate(fallback.definition(), invocation.getArguments());
                if (fallbackValidation != null) continue;
                recoveries++;
                CapabilityInvocation fallbackInvocation = new CapabilityInvocation(fallbackCode, invocation.getArguments());
                context.setCurrentCapabilityInvocation(fallbackInvocation);
                result = invokeOnce(fallback, intent, context, fallback.definition().getTimeout());
                result = validateOutput(fallback.definition(), result);
                actualCapabilityCode = fallbackCode;
                if (result.isSuccess() || recoveries >= remainingRecoveries) break;
            }
        }
        return new CapabilityCallResult(result, recoveries, actualCapabilityCode, compensationAttempted,
                elapsedMillis(startedAt));
        } finally {
            if (locked) SIDE_EFFECT_LOCK.unlock();
        }
    }

    private CapabilityResult compensateOnce(AiCapability capability, IntentCandidate intent,
                                            AiExecutionContext context, CapabilityInvocation invocation,
                                            CapabilityResult failedResult, Duration timeout) {
        return CapabilityExecutionSupport.execute(EXECUTOR,
                () -> capability.compensate(intent, context, invocation, failedResult), timeout,
                new CapabilityExecutionSupport.FailureMessages(
                        "EMPTY_COMPENSATION_RESULT", "补偿未返回结果",
                        "COMPENSATION_TIMEOUT", "能力补偿超时", false,
                        "COMPENSATION_INTERRUPTED", "能力补偿被中断",
                        "COMPENSATION_EXCEPTION", false));
    }

    private CapabilityResult invokeOnce(AiCapability capability, IntentCandidate intent,
                                        AiExecutionContext context, Duration timeout) {
        return CapabilityExecutionSupport.execute(EXECUTOR,
                () -> capability.invoke(intent, context), timeout,
                new CapabilityExecutionSupport.FailureMessages(
                        "EMPTY_RESULT", "能力未返回结果",
                        "CAPABILITY_TIMEOUT", "能力调用超时", true,
                        "CAPABILITY_INTERRUPTED", "能力调用被中断",
                        "CAPABILITY_EXCEPTION", true));
    }

    private CapabilityResult validateOutput(CapabilityDefinition definition, CapabilityResult result) {
        if (result == null || !result.isSuccess()) return result;
        String validationMessage = invocationValidator.validateOutput(definition, result.getOutput());
        return validationMessage == null ? result
                : CapabilityResult.failure("INVALID_OUTPUT", validationMessage, false);
    }

    private static CapabilityCallResult failure(String code, String message, int recoveries,
                                                String actualCapabilityCode, long startedAt) {
        return new CapabilityCallResult(CapabilityResult.failure(code, message, false), recoveries,
                actualCapabilityCode, false, elapsedMillis(startedAt));
    }

    private static long elapsedMillis(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
