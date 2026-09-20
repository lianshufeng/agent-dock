package com.github.agentdock.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agentdock.core.capability.AiCapability;
import com.github.agentdock.core.intent.LlmIntentAnalyzer;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.store.ExecutionCheckpointStore;
import com.github.agentdock.core.planning.*;
import com.github.agentdock.core.type.IntentComplexity;
import com.github.agentdock.core.type.IntentStatus;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionCheckpointTest {
    @Test
    void resumedExecutionSkipsCompletedCapability() {
        AtomicInteger analyzed = new AtomicInteger();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        InterruptOnceStore store = new InterruptOnceStore();
        AiKernel kernel = new AiKernel().registerExecutionCheckpointStore(store)
                .registerIntent(new IntentDefinition("ACTION", "执行动作"))
                .registerIntentAnalyzer((LlmIntentAnalyzer) (conversation, catalog) -> {
                    analyzed.incrementAndGet();
                    return new IntentAdapterResult(List.of(intent("first", List.of()),
                            intent("second", List.of("first"))), ContextRequirement.NONE, null);
                })
                .registerCapability(capability("first", firstCalls))
                .registerCapability(capability("second", secondCalls));
        ConversationContext input = new ConversationContext("session", "same-run", "user", "执行两个动作", List.of(), Map.of());

        assertThrows(IllegalStateException.class, () -> kernel.execute(input));
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
        ConversationResult resumed = kernel.execute(input);
        assertEquals(IntentStatus.SUCCESS, resumed.getStatus());
        assertEquals(1, analyzed.get());
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
        assertEquals(IntentStatus.SUCCESS, kernel.execute(input).getStatus());
        assertEquals(1, secondCalls.get());
    }

    @Test
    void interruptedRunningNodeIsNotReplayed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        InterruptOnceStore store = new InterruptOnceStore();
        store.interrupted = true;
        ExecutionPlan plan = ExecutionPlan.from("run", List.of(intent("first", List.of())));
        plan.getNodes().get("first").setStatus(PlanNodeStatus.RUNNING);
        ExecutionSnapshot snapshot = new ExecutionSnapshot();
        snapshot.setPlan(plan);
        store.value = new ObjectMapper().writeValueAsString(snapshot);
        AiKernel kernel = new AiKernel().registerExecutionCheckpointStore(store)
                .registerCapability(capability("first", calls));

        ConversationResult result = kernel.execute(new ConversationContext("session", "run", "user",
                "继续", List.of(), Map.of()));

        assertEquals(IntentStatus.WAITING_USER, result.getStatus());
        assertEquals(0, calls.get());
    }

    private IntentCandidate intent(String id, List<String> dependencies) {
        return new IntentCandidate(id, "ACTION", id, id + " 已完成", 1, 0, 0, dependencies,
                IntentComplexity.SIMPLE, ContextRequirement.NONE, new CapabilityInvocation(id, Map.of()));
    }

    private AiCapability capability(String code, AtomicInteger calls) {
        return new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition(code, code, Map.of(), Map.of("value", "string"),
                        true, false, false, Duration.ofSeconds(5), 0, List.of()).terminal();
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                calls.incrementAndGet();
                return CapabilityResult.success(Map.of("value", code));
            }
        };
    }

    private static final class InterruptOnceStore implements ExecutionCheckpointStore {
        String value;
        boolean interrupted;
        public Optional<String> load(String conversationId, String executionId) { return Optional.ofNullable(value); }
        public void save(String conversationId, String executionId, String snapshot) {
            value = snapshot;
            if (!interrupted && snapshot.contains("\"status\":\"SUCCESS\"")) {
                interrupted = true;
                throw new IllegalStateException("simulated interruption after checkpoint");
            }
        }
    }
}
