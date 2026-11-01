package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityResult;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** 能力调用与补偿调用共用的超时、取消和异常转换逻辑。 */
final class CapabilityExecutionSupport {

    private CapabilityExecutionSupport() {
    }

    static CapabilityResult execute(ExecutorService executor, Callable<CapabilityResult> action,
                                    Duration timeout, FailureMessages messages) {
        TrackedFutureTask future = new TrackedFutureTask(action);
        try {
            executor.execute(future);
        } catch (RejectedExecutionException exception) {
            return CapabilityResult.failure("CAPABILITY_EXECUTOR_REJECTED", "能力执行队列已满或关闭，未启动调用", false);
        }
        try {
            Duration effectiveTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                    ? Duration.ofSeconds(30) : timeout;
            CapabilityResult result = future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return result == null ? CapabilityResult.failure(messages.emptyCode(), messages.emptyMessage(), false) : result;
        } catch (TimeoutException exception) {
            if (future.cancelBeforeStart()) {
                removeQueued(executor, future);
                return CapabilityResult.failure("CAPABILITY_EXECUTOR_QUEUE_TIMEOUT", "能力执行队列等待超时，调用未启动", false);
            }
            if (!future.cancel(true)) return completedResult(future, messages);
            return CapabilityResult.failure(messages.timeoutCode(), messages.timeoutMessage(), messages.timeoutRetryable());
        } catch (InterruptedException exception) {
            if (future.cancelBeforeStart()) {
                removeQueued(executor, future);
                Thread.currentThread().interrupt();
                return CapabilityResult.failure("CAPABILITY_EXECUTOR_QUEUE_INTERRUPTED", "等待能力执行队列时被中断，调用未启动", false);
            }
            future.cancel(true);
            Thread.currentThread().interrupt();
            return CapabilityResult.failure(messages.interruptedCode(), messages.interruptedMessage(), false);
        } catch (ExecutionException exception) {
            return executionFailure(exception, messages);
        }
    }

    private static CapabilityResult completedResult(Future<CapabilityResult> future, FailureMessages messages) {
        try {
            CapabilityResult result = future.get();
            return result == null ? CapabilityResult.failure(messages.emptyCode(), messages.emptyMessage(), false) : result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CapabilityResult.failure(messages.interruptedCode(), messages.interruptedMessage(), false);
        } catch (ExecutionException exception) {
            return executionFailure(exception, messages);
        }
    }

    private static CapabilityResult executionFailure(ExecutionException exception, FailureMessages messages) {
        Throwable cause = exception.getCause();
        return CapabilityResult.failure(messages.exceptionCode(),
                cause == null ? exception.getMessage() : cause.getMessage(), messages.exceptionRetryable());
    }

    private static void removeQueued(ExecutorService executor, Runnable task) {
        if (executor instanceof ThreadPoolExecutor threadPoolExecutor) threadPoolExecutor.remove(task);
    }

    private static final class TrackedFutureTask extends FutureTask<CapabilityResult> {
        private static final int QUEUED = 0;
        private static final int RUNNING = 1;
        private static final int CANCELLED_BEFORE_START = 2;
        private static final int FINISHED = 3;
        private static final int STARTING = 4;
        private final AtomicInteger state;

        private TrackedFutureTask(Callable<CapabilityResult> action) {
            this(action, new AtomicInteger(QUEUED));
        }

        private TrackedFutureTask(Callable<CapabilityResult> action, AtomicInteger state) {
            super(() -> {
                if (!state.compareAndSet(STARTING, RUNNING))
                    return CapabilityResult.failure("CAPABILITY_EXECUTOR_QUEUE_TIMEOUT", "能力执行队列等待超时，调用未启动", false);
                return action.call();
            });
            this.state = state;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(QUEUED, STARTING)) return;
            try {
                super.run();
            } finally {
                state.compareAndSet(RUNNING, FINISHED);
            }
        }

        private boolean cancelBeforeStart() {
            int current = state.get();
            while (current == QUEUED || current == STARTING) {
                if (state.compareAndSet(current, CANCELLED_BEFORE_START)) {
                    super.cancel(false);
                    return true;
                }
                current = state.get();
            }
            return false;
        }
    }

    record FailureMessages(String emptyCode, String emptyMessage,
                           String timeoutCode, String timeoutMessage, boolean timeoutRetryable,
                           String interruptedCode, String interruptedMessage,
                           String exceptionCode, boolean exceptionRetryable) {
    }
}
