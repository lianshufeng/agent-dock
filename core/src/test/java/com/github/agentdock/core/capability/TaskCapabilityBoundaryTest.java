package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaskCapabilityBoundaryTest {
    @Test
    void rejectsCapabilityOutsideRegisteredTaskBoundary() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.register(capability("allowed"));
        registry.register(capability("blocked"));
        AiExecutionContext context = new AiExecutionContext(new ConversationContext());
        Task task = new Task("task-1", "intent-1", "目标", "完成", List.of());
        task.setDefinitionCode("task.definition");
        task.setAllowedCapabilityCodes(List.of("allowed"));
        context.setCurrentTask(task);

        CapabilityCallResult result = new DefaultCapabilityInvoker(registry).invoke(
                new IntentCandidate(), context, new CapabilityInvocation("blocked", Map.of()), 1);

        assertFalse(result.result().isSuccess());
        assertEquals("TASK_CAPABILITY_NOT_ALLOWED", result.result().getErrorCode());
    }

    private AiCapability capability(String code) {
        CapabilityDefinition definition = new CapabilityDefinition(code, code, Map.of(), Map.of(),
                false, true, false, Duration.ofSeconds(1), 0, List.of());
        return new AiCapability() {
            @Override public CapabilityDefinition definition() { return definition; }
            @Override public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                return CapabilityResult.success(Map.of("code", code));
            }
        };
    }
}
