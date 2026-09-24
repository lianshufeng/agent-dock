package com.github.agentdock.core;

import com.github.agentdock.core.capability.AiCapability;
import com.github.agentdock.core.context.ContextSnapshot;
import com.github.agentdock.core.intent.LlmIntentAnalyzer;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.planning.*;
import com.github.agentdock.core.store.ExecutionCheckpointStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agentdock.core.type.IntentStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeferredBranchExecutionTest {
    private static final String INPUT = "你查询下明天重庆的下不下雨，然后如果不下雨就给我推荐一个去朝天门的旅游计划，以及计算大概费用；如果下雨就推荐一个火锅店及预算";

    @Test void dryWeatherRunsOnlyTripBranch() { assertBranch("2026-09-26 重庆不下雨", "dry", "朝天门行程", 2); }

    @Test void rainyWeatherRunsOnlyHotpotBranch() { assertBranch("2026-09-26 重庆下雨", "rain", "火锅店预算", 2); }

    @Test void uncertainWeatherDoesNotRunEitherBranch() { assertBranch("天气资料不足", "", null, 1); }

    @Test void wrongDateDoesNotSelectBranch() { assertBranch("2026-09-25 重庆不下雨", "", null, 1); }

    @Test void timeAnchorUsesUserLocalDate() {
        Map<String, String> anchor = com.github.agentdock.core.context.RequestTimeAnchor.from(Map.of(
                "runtimeEnvironment", Map.of("requestTimeUtc", "2026-09-25T16:10:00Z",
                        "userTimezone", "Asia/Shanghai")));
        assertEquals("2026-09-26", anchor.get("today"));
        assertEquals("2026-09-27", anchor.get("tomorrow"));
    }

    @Test void resumedSelectedBranchDoesNotRunWeatherOrDecideAgain() throws Exception {
        IntentCandidate weather = intent("weather", "查询天气", List.of());
        IntentCandidate trip = intent("trip", "朝天门行程", List.of("weather"));
        ExecutionPlan plan = ExecutionPlan.from("resume-branch", List.of(weather, trip));
        plan.getNodes().get("weather").setStatus(PlanNodeStatus.SUCCESS);
        DeferredBranch branch = branch();
        branch.setSelectedChoiceId("dry"); branch.setResolution("SELECTED");
        plan.setDeferredBranches(List.of(branch));
        plan.setRequestTimeAnchor(Map.of("today", "2026-09-25", "tomorrow", "2026-09-26"));
        ExecutionSnapshot snapshot = new ExecutionSnapshot();
        snapshot.setPlan(plan);
        snapshot.setResults(List.of(IntentResult.success(weather, "2026-09-26 重庆不下雨")));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String saved = mapper.writeValueAsString(snapshot);
        List<String> calls = new ArrayList<>();
        AiKernel kernel = new AiKernel().registerIntent(new IntentDefinition("TASK", "执行目标"))
                .registerExecutionCheckpointStore(new ExecutionCheckpointStore() {
                    private String value = saved;
                    @Override public Optional<String> load(String conversationId, String executionId) {
                        return Optional.of(value);
                    }
                    @Override public void save(String conversationId, String executionId, String serialized) {
                        value = serialized;
                    }
                })
                .registerCapability(new AiCapability() {
                    @Override public CapabilityDefinition definition() {
                        return new CapabilityDefinition("MOCK", "模拟结果", Map.of(), Map.of(), false,
                                true, false, Duration.ofSeconds(5), 0, List.of()).terminal();
                    }
                    @Override public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        calls.add(intent.getDescription());
                        return CapabilityResult.success(intent.getDescription());
                    }
                })
                .registerIntentLoopPlanner(request -> {
                    IntentLoopDecision decision = new IntentLoopDecision();
                    decision.setStatus(IntentLoopDecision.Status.CONTINUE);
                    CapabilityInvocation call = new CapabilityInvocation("MOCK", Map.of());
                    call.setInvocationId("real-call");
                    if (!request.getIntent().getDependsOn().isEmpty())
                        call.setDependsOnInvocationIds(request.getIntent().getDependsOn());
                    decision.setToolInvocations(List.of(call, new CapabilityInvocation()));
                    decision.setCompleteAfterTools(true);
                    return decision;
                });
        ConversationResult result = kernel.execute(new ConversationContext("sample", "resume-branch", "user",
                INPUT, List.of(), Map.of()));
        assertEquals(IntentStatus.SUCCESS, result.getStatus(), result.getIntentResults().toString());
        assertEquals(List.of("朝天门行程"), calls);
    }

    private void assertBranch(String weather, String selected, String expectedSecond, int expectedCalls) {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger decisions = new AtomicInteger();
        AiKernel kernel = new AiKernel().registerIntent(new IntentDefinition("TASK", "执行目标"))
                .registerIntentAnalyzer(new LlmIntentAnalyzer() {
                    @Override public IntentAdapterResult analyze(ConversationContext context, String catalog) {
                        IntentAdapterResult result = new IntentAdapterResult(
                                List.of(intent("weather", "查询 2026-09-26 重庆天气", List.of())), ContextRequirement.NONE, null);
                        result.setDeferredBranches(List.of(branch()));
                        return result;
                    }
                    @Override public DeferredBranchDecision resolveDeferredBranch(ConversationContext context,
                            String catalog, ContextSnapshot snapshot, DeferredBranch branch, IntentResult result) {
                        decisions.incrementAndGet();
                        DeferredBranchDecision decision = new DeferredBranchDecision();
                        String answer = String.valueOf(result.getOutput());
                        if (!answer.contains("2026-09-26")) return decision;
                        String choice = answer.contains("不下雨") ? "dry" : answer.contains("下雨") ? "rain" : "";
                        if (choice.isEmpty()) return decision;
                        decision.setSelectedChoiceId(choice);
                        decision.setReason(answer);
                        decision.setCandidates(List.of(intent(choice, choice.equals("dry") ? "朝天门行程" : "火锅店预算",
                                List.of("weather"))));
                        return decision;
                    }
                })
                .registerCapability(new AiCapability() {
                    @Override public CapabilityDefinition definition() {
                        return new CapabilityDefinition("MOCK", "模拟结果", Map.of(), Map.of(), false,
                                true, false, Duration.ofSeconds(5), 0, List.of()).terminal();
                    }
                    @Override public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        calls.add(intent.getDescription());
                        return CapabilityResult.success(intent.getId().equals("weather") ? weather : intent.getDescription());
                    }
                })
                .registerIntentLoopPlanner(request -> {
                    IntentLoopDecision decision = new IntentLoopDecision();
                    decision.setStatus(IntentLoopDecision.Status.CONTINUE);
                    decision.setToolInvocations(List.of(new CapabilityInvocation("MOCK", Map.of())));
                    decision.setCompleteAfterTools(true);
                    return decision;
                });
        ConversationContext context = new ConversationContext("sample", UUID.randomUUID().toString(), "user",
                INPUT, List.of(), Map.of("runtimeEnvironment", Map.of("requestTimeUtc", "2026-09-25T02:00:00Z",
                        "userTimezone", "Asia/Shanghai")));
        ConversationResult result = kernel.execute(context);
        assertEquals(expectedCalls, calls.size());
        assertEquals(1, decisions.get());
        assertEquals("查询 2026-09-26 重庆天气", calls.get(0));
        if (expectedSecond == null) assertEquals(IntentStatus.WAITING_USER, result.getStatus());
        else {
            assertEquals(IntentStatus.SUCCESS, result.getStatus());
            assertEquals(expectedSecond, calls.get(1));
            assertEquals(2, result.getIntentResults().size());
        }
    }

    private static DeferredBranch branch() {
        DeferredBranch branch = new DeferredBranch();
        branch.setId("weather-choice"); branch.setTriggerIntentId("weather");
        DeferredBranch.Choice dry = new DeferredBranch.Choice();
        dry.setId("dry"); dry.setCondition("不下雨"); dry.setGoal("朝天门行程和费用");
        DeferredBranch.Choice rain = new DeferredBranch.Choice();
        rain.setId("rain"); rain.setCondition("下雨"); rain.setGoal("火锅店和预算");
        branch.setChoices(List.of(dry, rain));
        return branch;
    }

    private static IntentCandidate intent(String id, String description, List<String> dependencies) {
        IntentCandidate intent = new IntentCandidate();
        intent.setId(id); intent.setCode("TASK"); intent.setDescription(description);
        intent.setExpectedResult(description); intent.setConfidence(1);
        intent.setDependsOn(dependencies);
        intent.setContextRequirement(ContextRequirement.NONE);
        return intent;
    }
}
