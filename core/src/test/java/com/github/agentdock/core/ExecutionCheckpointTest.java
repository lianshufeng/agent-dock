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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionCheckpointTest {
    @Test void restoredPlanSkipsHandledSteeringAndKeepsMergedConstraints() throws Exception {
        InterruptOnceStore store = new InterruptOnceStore(); store.interrupted = true;
        ExecutionPlan plan = ExecutionPlan.from("run-steer", List.of(intent("pending", List.of())));
        plan.getHandledSteeringInputIds().add("steer-1");
        ExecutionSnapshot snapshot = new ExecutionSnapshot(); snapshot.setPlan(plan);
        snapshot.setExecutionInput("原始目标\n\n只修改备注，不改名称");
        store.value = new ObjectMapper().writeValueAsString(snapshot);
        AtomicInteger calls = new AtomicInteger();
        AiKernel kernel = new AiKernel().registerExecutionCheckpointStore(store)
                .registerCapability(new AiCapability() {
                    public CapabilityDefinition definition() {
                        return new CapabilityDefinition("pending", "写入", Map.of(), Map.of(), true, false, false,
                                Duration.ofSeconds(5), 0, List.of()).terminal();
                    }
                    public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        assertTrue(context.getConversation().getUserInput().contains("只修改备注，不改名称"));
                        calls.incrementAndGet(); return CapabilityResult.success("done");
                    }
                }).registerIntentAnalyzer((conversation, catalog) -> {
                    fail("已处理的补充输入不得在恢复时重复分类"); return null;
                });
        var steering = new com.github.agentdock.core.steering.ExecutionSteering();
        steering.offer(new com.github.agentdock.core.steering.SteeringInput("steer-1", "只修改备注，不改名称", 1, Map.of()));
        ConversationResult result = kernel.execute(new ConversationContext("session", "run-steer", "user",
                "原始目标", List.of(), Map.of()), steering);
        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void resumedExecutionSkipsCompletedCapability() {
        AtomicInteger analyzed = new AtomicInteger();
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        InterruptOnceStore store = new InterruptOnceStore();
        AiKernel kernel = new AiKernel().registerExecutionObservation(true).registerExecutionCheckpointStore(store)
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
    void completedCheckpointCanBeFinalizedWithoutReplayingCapability() throws Exception {
        InterruptOnceStore store = new InterruptOnceStore();
        store.interrupted = true;
        IntentCandidate completed = intent("completed", List.of());
        ExecutionPlan plan = ExecutionPlan.from("finalize-run", List.of(completed));
        plan.getNodes().get("completed").setStatus(PlanNodeStatus.SUCCESS);
        ExecutionSnapshot snapshot = new ExecutionSnapshot();
        snapshot.setPlan(plan);
        snapshot.setResults(List.of(IntentResult.success(completed, Map.of("value", "already-done"))));
        store.value = new ObjectMapper().writeValueAsString(snapshot);

        AtomicInteger calls = new AtomicInteger();
        AiKernel kernel = new AiKernel().registerExecutionCheckpointStore(store)
                .registerCapability(capability("completed", calls))
                .registerIntentAnalyzer((conversation, catalog) -> {
                    fail("已有检查点不应重新分析意图");
                    return null;
                });

        assertDoesNotThrow(() -> kernel.validateExecutionResume("session", "finalize-run"));
        ConversationResult result = kernel.execute(new ConversationContext("session", "finalize-run", "user",
                "完成后的请求", List.of(), Map.of()));

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(0, calls.get());
        ExecutionSnapshot finalized = new ObjectMapper().readValue(store.value, ExecutionSnapshot.class);
        assertNotNull(finalized.getFinalResult());
        assertThrows(IllegalArgumentException.class,
                () -> kernel.validateExecutionResume("session", "finalize-run"));
    }

    @Test
    void resumeRejectsUncertainOrIncompleteCheckpoints() throws Exception {
        InterruptOnceStore store = new InterruptOnceStore();
        AiKernel kernel = new AiKernel().registerExecutionCheckpointStore(store);
        ObjectMapper mapper = new ObjectMapper();
        for (PlanNodeStatus status : List.of(PlanNodeStatus.RUNNING, PlanNodeStatus.FAILED,
                PlanNodeStatus.WAITING_USER, PlanNodeStatus.SKIPPED, PlanNodeStatus.CANCELLED)) {
            ExecutionPlan plan = ExecutionPlan.from("unsafe-run", List.of(intent("node", List.of())));
            plan.getNodes().get("node").setStatus(status);
            ExecutionSnapshot snapshot = new ExecutionSnapshot();
            snapshot.setPlan(plan);
            store.value = mapper.writeValueAsString(snapshot);
            assertThrows(IllegalArgumentException.class,
                    () -> kernel.validateExecutionResume("session", "unsafe-run"), status.name());
        }

        IntentCandidate completed = intent("completed", List.of());
        ExecutionPlan incompletePlan = ExecutionPlan.from("incomplete-run", List.of(completed));
        incompletePlan.getNodes().get("completed").setStatus(PlanNodeStatus.SUCCESS);
        ExecutionSnapshot incomplete = new ExecutionSnapshot();
        incomplete.setPlan(incompletePlan);
        incomplete.setResults(List.of());
        store.value = mapper.writeValueAsString(incomplete);
        assertThrows(IllegalArgumentException.class,
                () -> kernel.validateExecutionResume("session", "incomplete-run"));

        ExecutionPlan blockedPlan = ExecutionPlan.from("blocked-run", List.of(
                intent("first", List.of("second")), intent("second", List.of("first"))));
        ExecutionSnapshot blocked = new ExecutionSnapshot();
        blocked.setPlan(blockedPlan);
        store.value = mapper.writeValueAsString(blocked);
        assertThrows(IllegalArgumentException.class,
                () -> kernel.validateExecutionResume("session", "blocked-run"));

        ExecutionPlan malformedPlan = ExecutionPlan.from("malformed-run", List.of());
        malformedPlan.getNodes().put("null", null);
        ExecutionSnapshot malformed = new ExecutionSnapshot();
        malformed.setPlan(malformedPlan);
        store.value = mapper.writeValueAsString(malformed);
        assertThrows(IllegalArgumentException.class,
                () -> kernel.validateExecutionResume("session", "malformed-run"));
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
        AiKernel kernel = new AiKernel().registerExecutionObservation(true).registerExecutionCheckpointStore(store)
                .registerCapability(capability("first", calls));

        ConversationResult result = kernel.execute(new ConversationContext("session", "run", "user",
                "继续", List.of(), Map.of()));

        assertEquals(IntentStatus.WAITING_USER, result.getStatus());
        assertEquals(0, calls.get());
    }

    @Test
    void rejectedDagSubmissionKeepsNodePendingForExplicitRetry() throws Exception {
        var field = AiExecutionEngine.class.getDeclaredField("DAG_EXECUTOR");
        field.setAccessible(true);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(null);
        int workers = executor.getMaximumPoolSize();
        int queuedTasks = executor.getQueue().remainingCapacity();
        CountDownLatch started = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(workers + queuedTasks);
        AtomicInteger analyzed = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        InterruptOnceStore store = new InterruptOnceStore();
        store.interrupted = true;
        AiKernel kernel = new AiKernel().registerExecutionCheckpointStore(store)
                .registerIntent(new IntentDefinition("ACTION", "执行动作"))
                .registerIntentAnalyzer((LlmIntentAnalyzer) (conversation, catalog) -> {
                    analyzed.incrementAndGet();
                    return new IntentAdapterResult(List.of(intent("pending", List.of())),
                            ContextRequirement.NONE, null);
                })
                .registerCapability(capability("pending", calls));
        ConversationContext input = new ConversationContext("session", "rejected-run", "user",
                "执行动作", List.of(), Map.of());
        Runnable blocker = () -> {
            started.countDown();
            try { release.await(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { finished.countDown(); }
        };
        try {
            for (int index = 0; index < workers; index++) executor.execute(blocker);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int index = 0; index < queuedTasks; index++) executor.execute(blocker);
            assertEquals(0, executor.getQueue().remainingCapacity());

            ConversationResult rejected = kernel.execute(input);
            assertEquals(IntentStatus.FAILED, rejected.getStatus());
            assertEquals(0, calls.get());
            ExecutionSnapshot saved = new ObjectMapper().readValue(store.value, ExecutionSnapshot.class);
            assertNull(saved.getFinalResult());
            assertEquals(PlanNodeStatus.PENDING, saved.getPlan().getNodes().get("pending").getStatus());
            assertDoesNotThrow(() -> kernel.validateExecutionResume("session", "rejected-run"));
        } finally {
            release.countDown();
            assertTrue(finished.await(10, TimeUnit.SECONDS));
        }

        ConversationResult retried = kernel.execute(input);
        assertEquals(IntentStatus.SUCCESS, retried.getStatus());
        assertEquals(1, analyzed.get());
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class,
                () -> kernel.validateExecutionResume("session", "rejected-run"));
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
