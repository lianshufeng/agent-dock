package com.github.agentdock.core.loop;

import com.github.agentdock.core.internal.ExecutorSupport;
import com.github.agentdock.core.model.IntentLoopDecision;
import com.github.agentdock.core.model.IntentLoopRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 仅负责规划调用的线程隔离、超时与中断，不决定任务是否完成。 */
final class PlannerCallRunner {
    private static final int PARALLELISM = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            PARALLELISM, PARALLELISM, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(8, PARALLELISM * 4)),
            ExecutorSupport.daemonThreadFactory("ai-intent-planner"),
            new ThreadPoolExecutor.AbortPolicy());

    private PlannerCallRunner() { }

    static IntentLoopDecision decide(IntentLoopPlanner planner, IntentLoopRequest request,
                                     Instant deadline, Duration maxDuration) {
        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMillis <= 0) return null;
        long timeoutMillis = Math.min(remainingMillis, maxDuration.toMillis());
        Future<IntentLoopDecision> future = EXECUTOR.submit(() -> planner.decide(request));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException("意图 Loop 规划调用超时", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("意图 Loop 规划调用被中断", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("意图 Loop 规划调用失败", exception.getCause());
        }
    }

    static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
