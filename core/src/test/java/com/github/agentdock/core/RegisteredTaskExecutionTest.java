package com.github.agentdock.core;

import com.github.agentdock.core.capability.AiCapability;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.IntentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RegisteredTaskExecutionTest {
    @Test void fixedWorkflowBindsRealOutputWithoutCallingPlanner() {
        AtomicInteger calls = new AtomicInteger();
        var first = new CapabilityInvocation("source", Map.of()); first.setInvocationId("first");
        var second = new CapabilityInvocation("sink", Map.of()); second.setInvocationId("second");
        second.setDependsOnInvocationIds(List.of("first"));
        var reference = new IntentInputReference("$task", "/output/value"); reference.setArgumentName("value");
        second.setInputRefs(List.of(reference));
        TaskDefinition definition = new TaskDefinition("workflow", "固定流程", Set.of("TEST"),
                List.of("source", "sink"), "工具完成并绑定真实数据");
        definition.setExecutionMode(com.github.agentdock.core.task.TaskExecutionMode.WORKFLOW);
        definition.setSteps(List.of(second, first));
        AiKernel kernel = kernel().registerTask(definition)
                .registerCapability(new AiCapability() {
                    public CapabilityDefinition definition() {
                        return new CapabilityDefinition("source", "读取", Map.of(), Map.of("value", "string"),
                                false, true, false, null, 0, List.of());
                    }
                    public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        calls.incrementAndGet(); return CapabilityResult.success(Map.of("value", "真实数据"));
                    }
                }).registerCapability(new AiCapability() {
                    public CapabilityDefinition definition() {
                        return new CapabilityDefinition("sink", "处理", Map.of("value", "string"), Map.of(),
                                false, true, false, null, 0, List.of());
                    }
                    public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        assertEquals("真实数据", context.getCurrentCapabilityInvocation().getArguments().get("value"));
                        calls.incrementAndGet(); return CapabilityResult.success("处理完成");
                    }
                }).registerIntentLoopPlanner(request -> { throw new AssertionError("固定流程不能调用模型规划器"); });
        assertEquals(IntentStatus.SUCCESS, kernel.execute(input()).getStatus());
        assertEquals(2, calls.get());
        assertEquals("$task", second.getInputRefs().get(0).getIntentId());
    }

    @Test void fixedWorkflowPreflightsMissingDependencyBeforeAnyToolRuns() {
        AtomicInteger calls = new AtomicInteger();
        var first = new CapabilityInvocation("allowed", Map.of()); first.setInvocationId("first");
        var second = new CapabilityInvocation("allowed", Map.of()); second.setInvocationId("second");
        second.setDependsOnInvocationIds(List.of("missing"));
        TaskDefinition definition = new TaskDefinition("workflow", "固定流程", Set.of("TEST"), List.of("allowed"), "完成");
        definition.setExecutionMode(com.github.agentdock.core.task.TaskExecutionMode.WORKFLOW);
        definition.setSteps(List.of(first, second));
        assertEquals(IntentStatus.FAILED, kernel().registerTask(definition)
                .registerCapability(capability("allowed", calls)).execute(input()).getStatus());
        assertEquals(0, calls.get());
    }

    @Test void registeredBoundaryFiltersPlannerAndRejectsForbiddenInvocation() {
        AtomicInteger calls = new AtomicInteger();
        AiKernel kernel = kernel().registerCapability(capability("allowed", calls))
                .registerCapability(capability("blocked", calls))
                .registerTask(new TaskDefinition("reply", "回复", Set.of("TEST"), List.of("allowed"), "真实回复"))
                .registerIntentLoopPlanner(request -> {
                    assertEquals(List.of("allowed"), request.getCapabilities().stream().map(CapabilityDefinition::getCode).toList());
                    return decision("blocked");
                });
        assertEquals(IntentStatus.FAILED, kernel.execute(input()).getStatus());
        assertEquals(0, calls.get());
    }

    @Test void permittedToolRunsWithOriginalUserObjective() {
        AtomicInteger calls = new AtomicInteger();
        AiKernel kernel = kernel().registerCapability(capability("allowed", calls))
                .registerTask(new TaskDefinition("reply", "宽泛任务说明", Set.of("TEST"), List.of("allowed"), "真实回复"))
                .registerIntentLoopPlanner(request -> {
                    assertEquals("用户本次的具体目标", request.getIntent().getDescription());
                    return decision("allowed");
                });
        assertEquals(IntentStatus.SUCCESS, kernel.execute(input()).getStatus());
        assertEquals(1, calls.get());
    }

    @Test void customDomainPlannerKeepsBindingsAndExplicitTaskSelection() {
        AtomicInteger calls = new AtomicInteger();
        AiKernel kernel = kernel().registerCapability(capability("allowed", calls))
                .registerTask(new TaskDefinition("reply", "回复", Set.of("TEST"), List.of("allowed"), "真实回复"))
                .registerTaskPlanner((intent, context) -> {
                    Task task = new com.github.agentdock.core.task.DefaultTaskPlanner().plan(intent, context);
                    task.setDefinitionCode("reply");
                    task.setInputBindings(Map.of("domainEvidence", "保留领域上下文"));
                    return task;
                }).registerTaskVerifier((task, result, context) -> {
                    assertEquals("保留领域上下文", task.getInputBindings().get("domainEvidence"));
                    assertEquals("reply", task.getDefinitionCode());
                    return TaskVerification.passed();
                }).registerIntentLoopPlanner(request -> decision("allowed"));
        assertEquals(IntentStatus.SUCCESS, kernel.execute(input()).getStatus());
    }

    private AiKernel kernel() {
        return new AiKernel().registerIntent(new IntentDefinition("TEST", "测试目标"))
                .registerIntentAnalyzer((conversation, catalog) -> {
                    IntentCandidate intent = new IntentCandidate();
                    intent.setId("one"); intent.setCode("TEST"); intent.setConfidence(1);
                    intent.setDescription("用户本次的具体目标"); intent.setExpectedResult("得到真实结果");
                    intent.setDependsOn(List.of());
                    return new IntentAdapterResult(List.of(intent), ContextRequirement.NONE, null);
                });
    }

    private IntentLoopDecision decision(String code) {
        IntentLoopDecision decision = new IntentLoopDecision();
        decision.setStatus(IntentLoopDecision.Status.CONTINUE);
        decision.setToolInvocations(List.of(new CapabilityInvocation(code, Map.of())));
        decision.setCompleteAfterTools(true);
        return decision;
    }

    private AiCapability capability(String code, AtomicInteger calls) {
        return new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition(code, "工具", Map.of(), Map.of(), false, true, false, null, 0, List.of()).terminal();
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                calls.incrementAndGet(); return CapabilityResult.success(Map.of("answer", "真实结果"));
            }
        };
    }

    private ConversationContext input() {
        return new ConversationContext("acceptance", java.util.UUID.randomUUID().toString(), "user", "测试", List.of(), Map.of());
    }
}
