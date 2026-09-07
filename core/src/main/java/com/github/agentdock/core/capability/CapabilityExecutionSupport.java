package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityResult;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 能力调用与补偿调用共用的超时、取消和异常转换逻辑。 */
final class CapabilityExecutionSupport {

    private CapabilityExecutionSupport() {
    }

    static CapabilityResult execute(ExecutorService executor, Callable<CapabilityResult> action,
                                    Duration timeout, FailureMessages messages) {
        Future<CapabilityResult> future = executor.submit(action);
        try {
            Duration effectiveTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                    ? Duration.ofSeconds(30) : timeout;
            CapabilityResult result = future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return result == null ? CapabilityResult.failure(messages.emptyCode(), messages.emptyMessage(), false) : result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            return CapabilityResult.failure(messages.timeoutCode(), messages.timeoutMessage(), messages.timeoutRetryable());
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return CapabilityResult.failure(messages.interruptedCode(), messages.interruptedMessage(), false);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            return CapabilityResult.failure(messages.exceptionCode(),
                    cause == null ? exception.getMessage() : cause.getMessage(), messages.exceptionRetryable());
        }
    }

    record FailureMessages(String emptyCode, String emptyMessage,
                           String timeoutCode, String timeoutMessage, boolean timeoutRetryable,
                           String interruptedCode, String interruptedMessage,
                           String exceptionCode, boolean exceptionRetryable) {
    }
}
