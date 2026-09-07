package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
