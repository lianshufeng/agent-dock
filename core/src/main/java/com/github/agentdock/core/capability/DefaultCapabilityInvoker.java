package com.github.agentdock.core.capability;

import com.github.agentdock.core.internal.ExecutorSupport;
import com.github.agentdock.core.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/** 基于 JDK 的默认调用器；宿主可以注册其他线程模型的实现。 */
public final class DefaultCapabilityInvoker implements CapabilityInvoker {
    private static final int CPU_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int CAPABILITY_PARALLELISM = Math.max(1, CPU_COUNT / 4);
    /** 串行化副作用工具。工作线程持有调用直到真实退出，调用方超时不会释放写入互斥边界。 */
    private static final ExecutorService SIDE_EFFECT_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128),
            ExecutorSupport.daemonThreadFactory("ai-side-effect-invoker"),
            new ThreadPoolExecutor.AbortPolicy());
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            CAPABILITY_PARALLELISM, CAPABILITY_PARALLELISM, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(8, CAPABILITY_PARALLELISM * 4)),
            ExecutorSupport.daemonThreadFactory("ai-capability-invoker"),
            new ThreadPoolExecutor.AbortPolicy());

    public void shutdown() {
        EXECUTOR.shutdownNow();
        SIDE_EFFECT_EXECUTOR.shutdownNow();
    }

    public static void shutdownExecutor() {
        EXECUTOR.shutdownNow();
        SIDE_EFFECT_EXECUTOR.shutdownNow();
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
        if (!context.permitsCapability(invocation.getCapabilityCode()))
            return failure("TASK_CAPABILITY_NOT_ALLOWED", "当前任务不允许调用能力: " + invocation.getCapabilityCode(),
                    0, invocation.getCapabilityCode(), startedAt);
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
        int recoveries = 0;
        boolean compensationAttempted = false;
        String actualCapabilityCode = invocation.getCapabilityCode();
        int retries = retryPolicy.retries(definition, remainingRecoveries);
        CapabilityResult result;
        do {
            context.setCurrentCapabilityInvocation(invocation);
            result = invokeOnce(capability, intent, context, definition.getTimeout(), definition.isSideEffect());
            result = validateOutput(definition, result);
            result = classifyUnknownOutcome(definition, result);
            if (result.isSuccess() || result.isOutcomeUnknown() || !result.isRetryable() || recoveries >= retries) break;
            recoveries++;
        } while (true);

        if (result.isOutcomeUnknown() && definition.isSideEffect() && capability.supportsUnknownOutcomeRecovery()) {
            CapabilityResult recovered = recoverUnknownOnce(capability, intent, context, invocation, result,
                    definition.getTimeout());
            recoveries++;
            if (recovered != null && recovered.isSuccess()) {
                CapabilityResult validatedRecovery = validateOutput(definition, recovered);
                result = validatedRecovery != null && validatedRecovery.isSuccess()
                        ? validatedRecovery
                        : CapabilityResult.unknown("未知写入的核实结果不符合能力输出契约，仍需人工核实");
            } else {
                String detail = recovered == null ? "核实能力未返回结果" : recovered.getMessage();
                result = CapabilityResult.unknown("写入结果仍未确认，请先核实实际数据，禁止重复提交：" + detail);
            }
        }

        if (!result.isSuccess() && !result.isOutcomeUnknown() && definition.isSideEffect() && definition.isCompensatable()
                && recoveries < remainingRecoveries) {
            recoveries++;
            compensationAttempted = true;
            CapabilityResult compensation = compensateOnce(capability, intent, context, invocation, result,
                    definition.getTimeout());
            if (!compensation.isSuccess()) {
                result = CapabilityResult.unknown("补偿未能确认完成，必须先核实实际数据：" + compensation.getMessage());
            }
        }

        if (!result.isSuccess() && !result.isOutcomeUnknown() && recoveries < remainingRecoveries) {
            for (String fallbackCode : safe(definition.getFallbackCapabilities())) {
                if (!context.permitsCapability(fallbackCode)) continue;
                AiCapability fallback = registry.find(fallbackCode);
                if (fallback == null || !visibilityPolicy.isVisible(intent, fallback.definition(), context)) continue;
                String fallbackValidation = invocationValidator.validate(fallback.definition(), invocation.getArguments());
                if (fallbackValidation != null) continue;
                recoveries++;
                CapabilityInvocation fallbackInvocation = new CapabilityInvocation(fallbackCode, invocation.getArguments());
                context.setCurrentCapabilityInvocation(fallbackInvocation);
                CapabilityDefinition fallbackDefinition = fallback.definition();
                result = invokeOnce(fallback, intent, context, fallbackDefinition.getTimeout(),
                        fallbackDefinition.isSideEffect());
                result = validateOutput(fallback.definition(), result);
                result = classifyUnknownOutcome(fallback.definition(), result);
                actualCapabilityCode = fallbackCode;
                if (result.isOutcomeUnknown() && fallbackDefinition.isSideEffect()
                        && fallback.supportsUnknownOutcomeRecovery()) {
                    CapabilityResult recovered = recoverUnknownOnce(fallback, intent, context, fallbackInvocation,
                            result, fallbackDefinition.getTimeout());
                    recoveries++;
                    if (recovered != null && recovered.isSuccess()) {
                        CapabilityResult validatedRecovery = validateOutput(fallbackDefinition, recovered);
                        result = validatedRecovery != null && validatedRecovery.isSuccess()
                                ? validatedRecovery
                                : CapabilityResult.unknown("替代能力的未知写入核实结果不符合输出契约，仍需人工核实");
                    } else {
                        String detail = recovered == null ? "核实能力未返回结果" : recovered.getMessage();
                        result = CapabilityResult.unknown("替代能力写入结果仍未确认，请先核实实际数据，禁止重复提交：" + detail);
                    }
                }
                if (result.isSuccess() || result.isOutcomeUnknown() || recoveries >= remainingRecoveries) break;
            }
        }
        return new CapabilityCallResult(result, recoveries, actualCapabilityCode, compensationAttempted,
                elapsedMillis(startedAt));
    }

    private CapabilityResult compensateOnce(AiCapability capability, IntentCandidate intent,
                                            AiExecutionContext context, CapabilityInvocation invocation,
                                            CapabilityResult failedResult, Duration timeout) {
        return CapabilityExecutionSupport.execute(SIDE_EFFECT_EXECUTOR,
                () -> capability.compensate(intent, context, invocation, failedResult), timeout,
                new CapabilityExecutionSupport.FailureMessages(
                        "EMPTY_COMPENSATION_RESULT", "补偿未返回结果",
                        "COMPENSATION_TIMEOUT", "能力补偿超时", false,
                        "COMPENSATION_INTERRUPTED", "能力补偿被中断",
                        "COMPENSATION_EXCEPTION", false));
    }

    private CapabilityResult recoverUnknownOnce(AiCapability capability, IntentCandidate intent,
                                                AiExecutionContext context, CapabilityInvocation invocation,
                                                CapabilityResult previousUnknown, Duration timeout) {
        return CapabilityExecutionSupport.execute(SIDE_EFFECT_EXECUTOR,
                () -> capability.recoverUnknownOutcome(intent, context, invocation, previousUnknown), timeout,
                new CapabilityExecutionSupport.FailureMessages(
                        "UNKNOWN_RECOVERY_EMPTY_RESULT", "未知结果核实未返回结果",
                        "UNKNOWN_RECOVERY_TIMEOUT", "未知结果核实超时", false,
                        "UNKNOWN_RECOVERY_INTERRUPTED", "未知结果核实被中断",
                        "UNKNOWN_RECOVERY_EXCEPTION", false));
    }

    private CapabilityResult invokeOnce(AiCapability capability, IntentCandidate intent,
                                        AiExecutionContext context, Duration timeout, boolean sideEffect) {
        return CapabilityExecutionSupport.execute(sideEffect ? SIDE_EFFECT_EXECUTOR : EXECUTOR,
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

    private CapabilityResult classifyUnknownOutcome(CapabilityDefinition definition, CapabilityResult result) {
        if (!definition.isSideEffect() || result.isSuccess() || result.isOutcomeUnknown()) return result;
        if (java.util.Set.of("CAPABILITY_TIMEOUT", "CAPABILITY_INTERRUPTED", "CAPABILITY_EXCEPTION",
                "EMPTY_RESULT", "INVALID_OUTPUT").contains(java.util.Objects.toString(result.getErrorCode(), "")))
            return CapabilityResult.unknown("外部操作结果尚未确定，请先核实实际状态，禁止重复执行：" + result.getMessage());
        return result;
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
