package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityExecutionSupportTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void returnsCapabilityResultWithoutChangingIt() {
        CapabilityResult expected = CapabilityResult.success("完成");

        CapabilityResult actual = CapabilityExecutionSupport.execute(executor, () -> expected,
                Duration.ofSeconds(1), capabilityMessages());

        assertSame(expected, actual);
    }

    @Test
    void convertsEmptyResultUsingConfiguredError() {
        CapabilityResult result = CapabilityExecutionSupport.execute(executor, () -> null,
                Duration.ofSeconds(1), capabilityMessages());

        assertFalse(result.isSuccess());
        assertEquals("EMPTY_RESULT", result.getErrorCode());
        assertEquals("能力未返回结果", result.getMessage());
        assertFalse(result.isRetryable());
    }

    @Test
    void convertsRejectedExecutorWithoutStartingCapability() {
        ExecutorService rejected = Executors.newSingleThreadExecutor();
        rejected.shutdownNow();

        CapabilityResult result = CapabilityExecutionSupport.execute(rejected,
                () -> CapabilityResult.success("不应执行"), Duration.ofSeconds(1), capabilityMessages());

        assertFalse(result.isSuccess());
        assertEquals("CAPABILITY_EXECUTOR_REJECTED", result.getErrorCode());
    }

    @Test
    void queuedTimeoutDoesNotStartCapabilityOrReportUnknownExecution() throws Exception {
        ExecutorService serial = Executors.newSingleThreadExecutor();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        AtomicInteger secondCalls = new AtomicInteger();
        try {
            Future<CapabilityResult> first = caller.submit(() -> CapabilityExecutionSupport.execute(serial, () -> {
                firstStarted.countDown();
                while (firstRelease.getCount() > 0) {
                    try { firstRelease.await(); } catch (InterruptedException ignored) { }
                }
                return CapabilityResult.success("首个能力完成");
            }, Duration.ofSeconds(2), capabilityMessages()));
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));

            CapabilityResult queued = CapabilityExecutionSupport.execute(serial, () -> {
                secondCalls.incrementAndGet();
                return CapabilityResult.success("不应执行");
            }, Duration.ofMillis(50), capabilityMessages());

            assertEquals("CAPABILITY_EXECUTOR_QUEUE_TIMEOUT", queued.getErrorCode());
            assertFalse(queued.isOutcomeUnknown());
            assertEquals(0, secondCalls.get());
            firstRelease.countDown();
            assertTrue(first.get(1, TimeUnit.SECONDS).isSuccess());
        } finally {
            firstRelease.countDown();
            caller.shutdownNow();
            serial.shutdownNow();
        }
    }

    @Test
    void convertsExecutionExceptionAndPreservesRetryableFlag() {
        CapabilityResult result = CapabilityExecutionSupport.execute(executor,
                () -> { throw new IllegalStateException("调用失败"); },
                Duration.ofSeconds(1), capabilityMessages());

        assertFalse(result.isSuccess());
        assertEquals("CAPABILITY_EXCEPTION", result.getErrorCode());
        assertEquals("调用失败", result.getMessage());
        assertTrue(result.isRetryable());
    }

    @Test
    void cancelsTimedOutExecutionAndUsesConfiguredError() {
        CapabilityResult result = CapabilityExecutionSupport.execute(executor, () -> {
            Thread.sleep(1_000);
            return CapabilityResult.success("不应返回");
        }, Duration.ofMillis(10), capabilityMessages());

        assertFalse(result.isSuccess());
        assertEquals("CAPABILITY_TIMEOUT", result.getErrorCode());
        assertEquals("能力调用超时", result.getMessage());
        assertTrue(result.isRetryable());
    }

    private CapabilityExecutionSupport.FailureMessages capabilityMessages() {
        return new CapabilityExecutionSupport.FailureMessages(
                "EMPTY_RESULT", "能力未返回结果",
                "CAPABILITY_TIMEOUT", "能力调用超时", true,
                "CAPABILITY_INTERRUPTED", "能力调用被中断",
                "CAPABILITY_EXCEPTION", true);
    }
}
