package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class UnknownOutcomeTest {
    @Test void timedOutSideEffectKeepsLaterWritesSerializedUntilWorkerExits() throws Exception {
        AtomicInteger activeWrites = new AtomicInteger();
        AtomicInteger maximumConcurrentWrites = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch firstExited = new CountDownLatch(1);
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("slow-write", "可忽略中断的写操作", Map.of(), Map.of(),
                        true, true, false, Duration.ofMillis(80), 0, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                int active = activeWrites.incrementAndGet();
                maximumConcurrentWrites.accumulateAndGet(active, Math::max);
                firstStarted.countDown();
                try {
                    while (firstRelease.getCount() > 0) {
                        try { firstRelease.await(); } catch (InterruptedException ignored) { }
                    }
                    return CapabilityResult.success("首个写入完成");
                } finally {
                    activeWrites.decrementAndGet();
                    firstExited.countDown();
                }
            }
        });
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("next-write", "后续写操作", Map.of(), Map.of(),
                        true, true, false, Duration.ofSeconds(3), 0, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                int active = activeWrites.incrementAndGet();
                maximumConcurrentWrites.accumulateAndGet(active, Math::max);
                secondStarted.countDown();
                try { return CapabilityResult.success("后续写入完成"); }
                finally { activeWrites.decrementAndGet(); }
            }
        });
        DefaultCapabilityInvoker invoker = new DefaultCapabilityInvoker(registry);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            CapabilityCallResult first = invoker.invoke(new IntentCandidate(), new AiExecutionContext(),
                    new CapabilityInvocation("slow-write", Map.of()), 0);
            assertTrue(firstStarted.await(1, TimeUnit.SECONDS));
            assertTrue(first.result().isOutcomeUnknown());

            Future<CapabilityCallResult> next = caller.submit(() -> invoker.invoke(new IntentCandidate(),
                    new AiExecutionContext(), new CapabilityInvocation("next-write", Map.of()), 0));
            assertFalse(secondStarted.await(200, TimeUnit.MILLISECONDS));
            assertEquals(1, activeWrites.get());

            firstRelease.countDown();
            assertTrue(firstExited.await(1, TimeUnit.SECONDS));
            assertTrue(next.get(3, TimeUnit.SECONDS).result().isSuccess());
            assertEquals(1, maximumConcurrentWrites.get());
        } finally {
            firstRelease.countDown();
            caller.shutdownNow();
        }
    }

    @Test void sideEffectTimeoutDoesNotRetryCompensateOrFallback() {
        AtomicInteger mutations = new AtomicInteger();
        AtomicInteger compensation = new AtomicInteger();
        AtomicInteger fallback = new AtomicInteger();
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("write", "写操作", Map.of(), Map.of(), true, true, true,
                        Duration.ofMillis(100), 2, List.of("fallback"));
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                mutations.incrementAndGet();
                try { Thread.sleep(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                return CapabilityResult.success("已产生写入");
            }
            public CapabilityResult compensate(IntentCandidate intent, AiExecutionContext context,
                                                CapabilityInvocation invocation, CapabilityResult result) {
                compensation.incrementAndGet(); return CapabilityResult.success("回滚");
            }
        });
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("fallback", "替代", Map.of(), Map.of(), true, true, false,
                        Duration.ofSeconds(1), 0, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                fallback.incrementAndGet(); return CapabilityResult.success("替代执行");
            }
        });
        CapabilityCallResult call = new DefaultCapabilityInvoker(registry).invoke(new IntentCandidate(),
                new AiExecutionContext(), new CapabilityInvocation("write", Map.of()), 3);
        assertTrue(call.result().isOutcomeUnknown());
        assertFalse(call.result().isRetryable());
        assertEquals("CAPABILITY_OUTCOME_UNKNOWN", call.result().getErrorCode());
        assertEquals(1, mutations.get());
        assertEquals(0, compensation.get());
        assertEquals(0, fallback.get());
        assertEquals(0, call.recoveryAttempts());
    }

    @Test void optedInCapabilityCanConfirmUnknownOutcomeWithoutRepeatingInitialCall() {
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger verifications = new AtomicInteger();
        CapabilityInvocation invocation = new CapabilityInvocation("recoverable-write", Map.of("operationId", "op-1"));
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("recoverable-write", "带幂等核实的写操作", Map.of("operationId", "string"),
                        Map.of("verified", "boolean"), true, true, false, Duration.ofSeconds(1), 0, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                writes.incrementAndGet();
                return CapabilityResult.unknown("写入回执丢失");
            }
            public boolean supportsUnknownOutcomeRecovery() { return true; }
            public CapabilityResult recoverUnknownOutcome(IntentCandidate intent, AiExecutionContext context,
                    CapabilityInvocation receivedInvocation, CapabilityResult previousUnknown) {
                verifications.incrementAndGet();
                assertSame(invocation, receivedInvocation);
                assertTrue(previousUnknown.isOutcomeUnknown());
                return CapabilityResult.success(Map.of("verified", true));
            }
        });

        CapabilityCallResult call = new DefaultCapabilityInvoker(registry).invoke(new IntentCandidate(),
                new AiExecutionContext(), invocation, 0);

        assertTrue(call.result().isSuccess(), call.result().toString());
        assertEquals(1, writes.get());
        assertEquals(1, verifications.get());
        assertEquals(1, call.recoveryAttempts());
    }

    @Test void invalidUnknownOutcomeRecoveryRemainsUnknown() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("invalid-recovery", "输出错误的核实操作", Map.of(),
                        Map.of("verified", "boolean"), true, true, false, Duration.ofSeconds(1), 0, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                return CapabilityResult.unknown("写入回执丢失");
            }
            public boolean supportsUnknownOutcomeRecovery() { return true; }
            public CapabilityResult recoverUnknownOutcome(IntentCandidate intent, AiExecutionContext context,
                    CapabilityInvocation invocation, CapabilityResult previousUnknown) {
                return CapabilityResult.success(Map.of("verified", "yes"));
            }
        });

        CapabilityCallResult call = new DefaultCapabilityInvoker(registry).invoke(new IntentCandidate(),
                new AiExecutionContext(), new CapabilityInvocation("invalid-recovery", Map.of()), 0);

        assertTrue(call.result().isOutcomeUnknown());
        assertEquals(1, call.recoveryAttempts());
    }
}
