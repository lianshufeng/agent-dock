package com.github.agentdock.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agentdock.core.capability.AiCapability;
import com.github.agentdock.core.context.ContextSnapshot;
import com.github.agentdock.core.intent.LlmIntentAnalyzer;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.steering.*;
import com.github.agentdock.core.type.AiEventType;
import com.github.agentdock.core.type.IntentStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SteeringExecutionTest {
    @Test
    void plannerCanReadSnakeCaseInputRefsWithoutLosingInvocation() throws Exception {
        CapabilityInvocation invocation = new ObjectMapper().readValue(
                "{\"capabilityCode\":\"MOCK\",\"arguments\":{},\"input_refs\":[]}",
                CapabilityInvocation.class);
        assertEquals("MOCK", invocation.getCapabilityCode());
        assertEquals(List.of(), invocation.getInputRefs());
    }

    @Test
    void earlyAdditionalInputPreservesOriginalPlanAndAllResults() {
        List<String> executed = new ArrayList<>();
        List<String> seenInputs = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.ADD, List.of(),
                List.of(intent("air", "空气质量", List.of())), executed, seenInputs);
        ExecutionSteering steering = new ExecutionSteering();
        assertEquals(SteeringOfferResult.ACCEPTED,
                steering.offer(new SteeringInput("extra", "顺便查询空气质量", 1, Map.of())));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议", "空气质量"), executed);
        assertEquals(3, result.getIntentResults().size());
        assertTrue(result.getMessage().contains("空气质量"));
        assertTrue(result.getMessage().contains("穿衣建议"));
        assertTrue(seenInputs.stream().allMatch(input -> input.contains("查询天气并给出北京穿衣建议")
                && input.contains("顺便查询空气质量")));
    }

    @Test
    void explicitReplacementOnlyChangesTargetIntent() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.REPLACE, List.of("advice"),
                List.of(intent("replacement", "上海穿衣建议", List.of())), executed);
        ExecutionSteering steering = new ExecutionSteering();
        steering.offer(new SteeringInput("extra", "把穿衣建议改成上海的", 1, Map.of()));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "上海穿衣建议"), executed);
        assertFalse(result.getMessage().contains("北京穿衣建议"));
    }

    @Test
    void unrelatedInsertionHintDoesNotTurnOldTaskIntoDependentTask() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.ADD, List.of("weather"),
                List.of(intent("air", "空气质量", List.of())), executed);
        ExecutionSteering steering = new ExecutionSteering();
        steering.offer(new SteeringInput("extra", "另外查询空气质量", 1, Map.of()));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议", "空气质量"), executed);
    }

    @Test
    void invalidSteeringKeepsOriginalPlan() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.CANCEL, List.of("missing"), List.of(), executed);
        ExecutionSteering steering = new ExecutionSteering();
        steering.offer(new SteeringInput("extra", "取消不存在的任务", 1, Map.of()));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议"), executed);
    }

    @Test
    void invalidReplacementTargetFallsBackToAddWithoutDroppingOriginalWork() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.REPLACE, List.of("missing"),
                List.of(intent("air", "空气质量", List.of())), executed);
        ExecutionSteering steering = new ExecutionSteering();
        steering.offer(new SteeringInput("extra", "顺便查询空气质量", 1, Map.of()));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议", "空气质量"), executed);
    }

    @Test
    void repeatedInputDecisionDoesNotChangePlan() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.NOOP, List.of(), List.of(), executed);
        ExecutionSteering steering = new ExecutionSteering();
        steering.offer(new SteeringInput("extra", "重复请求", 1, Map.of()));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议"), executed);
        assertEquals(2, result.getIntentResults().size());
    }

    @Test
    void unclearSupplementDoesNotStopOriginalWork() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.ADD, List.of(), List.of(), executed,
                new ArrayList<>(), "请说明要修改哪项任务");
        List<AiEventType> events = new ArrayList<>();
        kernel.subscribe(event -> events.add(event.getType()));
        ExecutionSteering steering = new ExecutionSteering();
        steering.offer(new SteeringInput("extra", "改一下", 1, Map.of()));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议"), executed);
        assertTrue(events.contains(AiEventType.WAITING_USER));
    }

    @Test
    void rapidSupplementBatchExecutesEachNewIntentOnce() {
        List<String> executed = new ArrayList<>();
        AiKernel kernel = kernel(SteeringAction.ADD, List.of(), List.of(
                intent("air", "空气质量", List.of()),
                intent("rain", "降雨概率", List.of()),
                intent("wind", "风速", List.of())), executed);
        ExecutionSteering steering = new ExecutionSteering();
        SteeringInput first = new SteeringInput("first", "空气质量", 1, Map.of());
        assertEquals(SteeringOfferResult.ACCEPTED, steering.offer(first));
        assertEquals(SteeringOfferResult.ACCEPTED,
                steering.offer(new SteeringInput("second", "降雨概率", 2, Map.of())));
        assertEquals(SteeringOfferResult.ACCEPTED,
                steering.offer(new SteeringInput("third", "风速", 3, Map.of())));
        assertEquals(SteeringOfferResult.DUPLICATE, steering.offer(first));

        ConversationResult result = kernel.execute(conversation(), steering);

        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(List.of("查询天气", "北京穿衣建议", "空气质量", "降雨概率", "风速"), executed);
        assertEquals(5, result.getIntentResults().size());
    }

    private AiKernel kernel(SteeringAction action, List<String> targets, List<IntentCandidate> extra,
                            List<String> executed) {
        return kernel(action, targets, extra, executed, new ArrayList<>());
    }

    private AiKernel kernel(SteeringAction action, List<String> targets, List<IntentCandidate> extra,
                            List<String> executed, List<String> seenInputs) {
        return kernel(action, targets, extra, executed, seenInputs, null);
    }

    private AiKernel kernel(SteeringAction action, List<String> targets, List<IntentCandidate> extra,
                            List<String> executed, List<String> seenInputs, String question) {
        return new AiKernel()
                .registerIntent(new IntentDefinition("TASK", "执行一项任务"))
                .registerIntentAnalyzer(new LlmIntentAnalyzer() {
                    @Override public IntentAdapterResult analyze(ConversationContext context, String catalog) {
                        return new IntentAdapterResult(List.of(intent("weather", "查询天气", List.of()),
                                intent("advice", "北京穿衣建议", List.of("weather"))),
                                ContextRequirement.NONE, null);
                    }
                    @Override public SteeringAdapterResult analyzeSteering(ConversationContext context,
                                                                            String catalog, ContextSnapshot snapshot) {
                        SteeringAdapterResult result = new SteeringAdapterResult();
                        result.setAction(action); result.setTargetNodeIds(targets); result.setCandidates(extra);
                        result.setClarificationQuestion(question);
                        return result;
                    }
                })
                .registerCapability(new AiCapability() {
                    @Override public CapabilityDefinition definition() {
                        return new CapabilityDefinition("MOCK", "返回任务结果", Map.of(), Map.of(),
                                false, true, false, null, 0, List.of()).terminal();
                    }
                    @Override public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        synchronized (executed) { executed.add(intent.getDescription()); }
                        synchronized (seenInputs) { seenInputs.add(context.getConversation().getUserInput()); }
                        return CapabilityResult.success(intent.getDescription());
                    }
                })
                .registerIntentLoopPlanner(request -> {
                    IntentLoopDecision decision = new IntentLoopDecision();
                    decision.setStatus(IntentLoopDecision.Status.CONTINUE);
                    decision.setToolInvocations(List.of(new CapabilityInvocation("MOCK", Map.of())));
                    decision.setCompleteAfterTools(true);
                    return decision;
                });
    }

    private ConversationContext conversation() {
        ConversationContext context = new ConversationContext();
        context.setUserInput("查询天气并给出北京穿衣建议");
        return context;
    }

    private IntentCandidate intent(String id, String description, List<String> dependencies) {
        IntentCandidate value = new IntentCandidate();
        value.setId(id); value.setCode("TASK"); value.setDescription(description);
        value.setExpectedResult(description); value.setConfidence(1);
        value.setDependsOn(dependencies);
        return value;
    }
}
