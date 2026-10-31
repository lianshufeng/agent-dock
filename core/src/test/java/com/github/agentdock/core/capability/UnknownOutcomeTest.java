package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UnknownOutcomeTest {
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
}
