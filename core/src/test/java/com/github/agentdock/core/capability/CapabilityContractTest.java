package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityContractTest {
    @Test
    void rejectsSuccessfulResultThatViolatesOutputContract() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(capability("primary", Map.of("count", "integer"),
                CapabilityResult.success(Map.of("count", "wrong"))));
        DefaultCapabilityInvoker invoker = new DefaultCapabilityInvoker(registry);

        CapabilityCallResult call = invoker.invoke(intent(), context(),
                new CapabilityInvocation("primary", Map.of()), 0);

        assertFalse(call.result().isSuccess());
        assertEquals("INVALID_OUTPUT", call.result().getErrorCode());
    }

    @Test
    void recordsActualFallbackCapabilityAndElapsedTime() {
        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityDefinition primaryDefinition = definition("primary", Map.of("value", "string"));
        primaryDefinition.setFallbackCapabilities(List.of("fallback"));
        registry.register(new AiCapability() {
            public CapabilityDefinition definition() { return primaryDefinition; }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                return CapabilityResult.failure("PRIMARY_FAILED", "failed", false);
            }
        });
        registry.register(capability("fallback", Map.of("value", "string"),
                CapabilityResult.success(Map.of("value", "ok"))));

        CapabilityCallResult call = new DefaultCapabilityInvoker(registry).invoke(intent(), context(),
                new CapabilityInvocation("primary", Map.of()), 1);

        assertTrue(call.result().isSuccess());
        assertEquals("fallback", call.actualCapabilityCode());
        assertEquals(1, call.recoveryAttempts());
        assertTrue(call.elapsedMillis() >= 0);
    }

    @Test
    void validatesUnknownInputBeforeInvocation() {
        CapabilityDefinition definition = definition("primary", Map.of("value", "string"));
        String issue = new CapabilityInvocationValidator().validate(definition, Map.of("unknown", true));
        assertEquals("能力参数包含未声明字段: unknown", issue);
    }

    private AiCapability capability(String code, Map<String, String> output, CapabilityResult result) {
        CapabilityDefinition definition = definition(code, output);
        return new AiCapability() {
            public CapabilityDefinition definition() { return definition; }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) { return result; }
        };
    }

    private CapabilityDefinition definition(String code, Map<String, String> output) {
        return new CapabilityDefinition(code, code, Map.of(), output, false, true, false,
                Duration.ofSeconds(1), 0, List.of());
    }

    private IntentCandidate intent() {
        IntentCandidate intent = new IntentCandidate();
        intent.setId("intent");
        intent.setCode("TEST");
        return intent;
    }

    private AiExecutionContext context() {
        return new AiExecutionContext(new ConversationContext());
    }
}
