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
    @Test void unknownToolOutcomeStopsLoopAndDependentIntent() {
        AtomicInteger calls = new AtomicInteger();
        AiKernel kernel = kernel().registerCapability(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("write", "写入", Map.of(), Map.of(), true, true, true, null, 2, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                calls.incrementAndGet(); return CapabilityResult.unknown("必须核实真实写入结果");
            }
        }).registerIntentAnalyzer((conversation, catalog) -> {
            IntentCandidate first = new IntentCandidate();
            first.setId("one"); first.setCode("TEST"); first.setConfidence(1);
            first.setDescription("写入"); first.setExpectedResult("完成写入"); first.setDependsOn(List.of());
            IntentCandidate next = new IntentCandidate();
            next.setId("two"); next.setCode("TEST"); next.setConfidence(1);
            next.setDescription("后续"); next.setExpectedResult("完成后续"); next.setDependsOn(List.of("one"));
            return new IntentAdapterResult(List.of(first, next), ContextRequirement.NONE, null);
        }).registerIntentLoopPlanner(request -> decision("write"))
                .registerTaskVerifier((task, result, context) -> TaskVerification.passed())
                .registerTaskReplanner((task, verification, context) -> {
                    fail("未知结果不能触发任务重规划");
                    return java.util.Optional.empty();
                });
        ConversationResult result = kernel.execute(input());
        assertEquals(IntentStatus.WAITING_USER, result.getIntentResults().stream()
                .filter(item -> "one".equals(item.getIntentId())).findFirst().orElseThrow().getStatus());
        assertEquals(IntentStatus.SKIPPED, result.getIntentResults().stream()
                .filter(item -> "two".equals(item.getIntentId())).findFirst().orElseThrow().getStatus());
        assertEquals(1, calls.get());
    }

    @Test void completedWorkflowWriteCannotBeReplayedByTaskReplanner() {
        AtomicInteger calls = new AtomicInteger();
        TaskDefinition definition = new TaskDefinition("workflow", "固定写入", Set.of("TEST"),
                List.of("write"), "写入后核验");
        definition.setExecutionMode(com.github.agentdock.core.task.TaskExecutionMode.WORKFLOW);
        definition.setSteps(List.of(new CapabilityInvocation("write", Map.of())));
        AiKernel kernel = kernel().registerTask(definition).registerCapability(new AiCapability() {
            public CapabilityDefinition definition() {
                return new CapabilityDefinition("write", "写入", Map.of(), Map.of(), true, false, false, null, 0, List.of());
            }
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                calls.incrementAndGet(); return CapabilityResult.success(Map.of("id", "real-id"));
            }
        }).registerTaskVerifier((task, result, context) -> TaskVerification.failed("仍需人工核验", true))
                .registerTaskReplanner((task, verification, context) -> {
                    fail("已经尝试写入，不能自动重放固定流程");
                    return java.util.Optional.of(task);
                });
        assertEquals(IntentStatus.FAILED, kernel.execute(input()).getStatus());
        assertEquals(1, calls.get());
    }

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
