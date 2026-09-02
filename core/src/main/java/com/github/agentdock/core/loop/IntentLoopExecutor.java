package com.github.agentdock.core.loop;

import com.github.agentdock.core.capability.*;
import com.github.agentdock.core.aggregation.CapabilityOutputCombiner;
import com.github.agentdock.core.aggregation.impl.DefaultCapabilityOutputCombiner;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.store.ChatHistoryStore;
import com.github.agentdock.core.event.*;
import com.github.agentdock.core.type.AiEventType;
import com.github.agentdock.core.type.IntentStatus;
import com.github.agentdock.core.task.*;
import com.github.agentdock.core.context.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 每个意图独立的有限执行循环；默认最多 3 次失败恢复，永不无限等待。 */
public final class IntentLoopExecutor {
    private static final Logger log = LoggerFactory.getLogger(IntentLoopExecutor.class);
    /** 工具失败会作为观察交回规划器，累计达到上限后才结束当前意图。 */
    public static final int MAX_FAILURE_RECOVERY = 3;
    /** 覆盖能力发现、数据获取、计算和一次校验/重规划，同时保持有限预算。 */
    public static final int MAX_ITERATIONS = 5;
    public static final int MAX_TOOL_CALLS = 12;
    public static final Duration MAX_INTENT_DURATION = Duration.ofMinutes(2);
    public static final Duration MAX_PLANNER_DURATION = Duration.ofSeconds(45);
    private static final int CPU_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int PLANNER_PARALLELISM = Math.max(1, CPU_COUNT / 4);
    private static final ExecutorService PLANNER_EXECUTOR = new ThreadPoolExecutor(
            PLANNER_PARALLELISM, PLANNER_PARALLELISM, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(Math.max(8, PLANNER_PARALLELISM * 4)),
            runnable -> {
        Thread thread = new Thread(runnable, "ai-intent-planner");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    public void shutdown() {
        PLANNER_EXECUTOR.shutdownNow();
    }

    public static void shutdownExecutor() {
        PLANNER_EXECUTOR.shutdownNow();
    }

    private final IntentLoopPlanner planner;
    private final CapabilityRegistry capabilities;
    private final ChatHistoryStore historyStore;
    private final CapabilityResolver capabilityResolver;
    private final CapabilityCandidateSelector capabilityCandidateSelector;
    private final CapabilityInvoker capabilityInvoker;
    private final AiEventPublisher events;
    private final CapabilityInvocationBinder invocationBinder = new CapabilityInvocationBinder();
    private final TaskPlanner taskPlanner;
    private final CapabilityOutputCombiner outputCombiner;
    private final TaskVerifier taskVerifier;
    private final TaskReplanner taskReplanner;
    private final ContextAssembler contextAssembler;

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore) {
        this(planner, capabilities, historyStore, new DefaultCapabilityResolver(),
                new DefaultCapabilityInvoker(capabilities), event -> { }, new DefaultTaskPlanner(),
                new DefaultCapabilityOutputCombiner(), new AllEligibleCapabilitySelector());
    }

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore,
                              CapabilityResolver capabilityResolver, CapabilityInvoker capabilityInvoker) {
        this(planner, capabilities, historyStore, capabilityResolver, capabilityInvoker, event -> { },
                new DefaultTaskPlanner(), new DefaultCapabilityOutputCombiner(), new AllEligibleCapabilitySelector());
    }

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore,
                              CapabilityResolver capabilityResolver, CapabilityInvoker capabilityInvoker,
                              AiEventPublisher events) {
        this(planner, capabilities, historyStore, capabilityResolver, capabilityInvoker, events,
                new DefaultTaskPlanner(), new DefaultCapabilityOutputCombiner(), new AllEligibleCapabilitySelector());
    }

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore,
                              CapabilityResolver capabilityResolver, CapabilityInvoker capabilityInvoker,
                              AiEventPublisher events, TaskPlanner taskPlanner) {
        this(planner, capabilities, historyStore, capabilityResolver, capabilityInvoker, events, taskPlanner,
                new DefaultCapabilityOutputCombiner(), new AllEligibleCapabilitySelector());
    }

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore,
                              CapabilityResolver capabilityResolver, CapabilityInvoker capabilityInvoker,
                              AiEventPublisher events, TaskPlanner taskPlanner,
                              CapabilityOutputCombiner outputCombiner) {
        this(planner, capabilities, historyStore, capabilityResolver, capabilityInvoker, events, taskPlanner,
                outputCombiner, new AllEligibleCapabilitySelector());
    }

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore,
                              CapabilityResolver capabilityResolver, CapabilityInvoker capabilityInvoker,
                              AiEventPublisher events, TaskPlanner taskPlanner,
                              CapabilityOutputCombiner outputCombiner,
                              CapabilityCandidateSelector capabilityCandidateSelector) {
        this(planner, capabilities, historyStore, capabilityResolver, capabilityInvoker, events, taskPlanner,
                outputCombiner, capabilityCandidateSelector, new DefaultContextAssembler(),
                new DefaultTaskVerifier(), new NoopTaskReplanner());
    }

    public IntentLoopExecutor(IntentLoopPlanner planner, CapabilityRegistry capabilities, ChatHistoryStore historyStore,
                              CapabilityResolver capabilityResolver, CapabilityInvoker capabilityInvoker,
                              AiEventPublisher events, TaskPlanner taskPlanner,
                              CapabilityOutputCombiner outputCombiner,
                              CapabilityCandidateSelector capabilityCandidateSelector,
                              ContextAssembler contextAssembler, TaskVerifier taskVerifier,
                              TaskReplanner taskReplanner) {
        this.planner = planner;
        this.capabilities = capabilities;
        this.historyStore = historyStore;
        this.capabilityResolver = capabilityResolver;
        this.capabilityCandidateSelector = capabilityCandidateSelector == null
                ? new AllEligibleCapabilitySelector() : capabilityCandidateSelector;
        this.capabilityInvoker = capabilityInvoker;
        this.events = events;
        this.taskPlanner = taskPlanner == null ? new DefaultTaskPlanner() : taskPlanner;
        this.outputCombiner = outputCombiner == null ? new DefaultCapabilityOutputCombiner() : outputCombiner;
        this.contextAssembler = contextAssembler == null ? new DefaultContextAssembler() : contextAssembler;
        this.taskVerifier = taskVerifier == null ? new DefaultTaskVerifier() : taskVerifier;
        this.taskReplanner = taskReplanner == null ? new NoopTaskReplanner() : taskReplanner;
    }

    public IntentResult execute(IntentCandidate intent, AiExecutionContext context) {
        Task task = taskPlanner.plan(intent, context);
        if (intent.getCapabilityInvocation() != null) {
            task.setCapabilityInvocation(intent.getCapabilityInvocation());
            // 兼容旧请求：能力调用归属任务后清除意图上的临时绑定。
            intent.setCapabilityInvocation(null);
        }
        task.setStatus(TaskStatus.RUNNING);
        publish(context, intent, AiEventType.TASK_CREATED, "创建执行任务：" + displayText(task.getObjective(), "处理当前请求"), 22,
                payload("taskId", task.getId(), "objective", task.getObjective(),
                        "successCriteria", task.getSuccessCriteria()));
        long start = System.nanoTime();
        int[] counts = new int[3];
        try {
            IntentResult result = executeInternal(intent, task, context, counts);
            publish(context, intent, AiEventType.VERIFICATION_STARTED, "正在验收任务结果", 76,
                    payload("taskId", task.getId(), "successCriteria", task.getSuccessCriteria()));
            TaskVerification verification = taskVerifier.verify(task, result, context);
            context.addVerification(intent.getId(), verification);
            if (!verification.isPassed() && verification.isReplanRequired()) {
                java.util.Optional<Task> replanned = taskReplanner.replan(task, verification, context);
                if (replanned.isPresent()) {
                    publish(context, intent, AiEventType.TASK_REPLANNED, "任务未满足成功标准，正在重新规划", 76,
                            java.util.Map.of("taskId", task.getId(), "replannedTaskId", replanned.get().getId(),
                                    "reason", verification.getMessage() == null ? "结果未通过检查" : verification.getMessage()));
                    IntentResult retryResult = executeInternal(intent, replanned.get(), context, counts);
                    TaskVerification retryVerification = taskVerifier.verify(replanned.get(), retryResult, context);
                    context.addVerification(intent.getId(), retryVerification);
                    if (retryVerification.isPassed()) {
                        result = retryResult;
                        verification = retryVerification;
                    }
                }
            }
            task.setStatus(verification.isPassed() ? TaskStatus.SUCCESS :
                    verification.isReplanRequired() ? TaskStatus.NEEDS_REPLAN : TaskStatus.FAILED);
            publish(context, intent, AiEventType.TASK_VERIFIED,
                    verification.isPassed() ? "结果已满足任务完成标准：" + displayText(task.getSuccessCriteria(), "已得到有效结果")
                            : "任务结果检查未通过：" + verification.getMessage(), 78,
                    payload("taskId", task.getId(), "successCriteria", task.getSuccessCriteria(),
                            "passed", verification.isPassed(), "replanRequired", verification.isReplanRequired()));
            publish(context, intent, verification.isPassed() ? AiEventType.VERIFICATION_PASSED : AiEventType.VERIFICATION_FAILED,
                    verification.isPassed() ? "任务结果验收通过" : "任务结果验收未通过：" + verification.getMessage(), 78,
                    payload("taskId", task.getId(), "action", verification.getAction(),
                            "evidence", verification.getEvidence()));
            if (verification.isPassed()) return result;
            String message = verification.getMessage() == null || verification.getMessage().isBlank()
                    ? "任务结果未通过验收" : verification.getMessage();
            if (verification.getAction() == VerificationAction.WAITING_USER
                    || verification.getAction() == VerificationAction.MANUAL_REVIEW) {
                return IntentResult.waitingUser(intent, message);
            }
            return IntentResult.failed(intent, message);
        } finally {
            publish(context, intent, AiEventType.INTENT_METRICS,
                    "任务执行完成：规划 " + counts[0] + " 次，调用工具 " + counts[1] + " 次", 80,
                    java.util.Map.of("plannerCalls", counts[0], "toolCalls", counts[1],
                            "confirmationOnly", counts[2] == 1, "hasSecondRound", counts[0] > 1,
                            "elapsedMillis", (System.nanoTime() - start) / 1_000_000));
        }
    }

    private IntentResult executeInternal(IntentCandidate intent, Task task, AiExecutionContext context, int[] counts) {
        log.info("AI 意图 Loop 开始 executionId={}, intentId={}, code={}", context.getConversation().getExecutionId(), intent.getId(), intent.getCode());
        if (task.getCapabilityInvocation() != null) {
            CapabilityInvocation invocation = task.getCapabilityInvocation();
            CapabilityDefinition definition = capabilities.definition(invocation.getCapabilityCode());
            if (definition == null) return IntentResult.failed(intent, "兜底能力未注册：" + invocation.getCapabilityCode());
            String requestedCapabilityCode = invocation.getCapabilityCode();
            boolean visible = capabilityResolver.resolve(intent, context, capabilities).stream()
                    .anyMatch(candidate -> requestedCapabilityCode.equals(candidate.getCode()));
            if (!visible) return IntentResult.failed(intent, "当前上下文不允许调用能力：" + invocation.getCapabilityCode());
            try {
                invocation = invocationBinder.bind(intent, context, invocation, definition);
                task.setCapabilityInvocation(invocation);
            } catch (IllegalArgumentException exception) {
                return IntentResult.failed(intent, exception.getMessage());
            }
            publishToolCalling(context, intent, invocation, definition);
            CapabilityCallResult call = capabilityInvoker.invoke(intent, context, invocation, MAX_FAILURE_RECOVERY);
            counts[1]++;
            log.info("AI 工具调用完成 executionId={}, intentId={}, capability={}, success={}, recoveries={}",
                    context.getConversation().getExecutionId(), intent.getId(), invocation.getCapabilityCode(),
                    call.result().isSuccess(), call.recoveryAttempts());
            publish(context, intent, AiEventType.TOOL_RESULT,
                    toolResultMessage(definition, call.result()), 65,
                    payload("requestedCapabilityCode", invocation.getCapabilityCode(),
                            "capabilityCode", call.actualCapabilityCode(), "capabilityDescription", definition.getDescription(),
                            "arguments", invocation.getArguments(), "result", call.result(), "recoveryAttempts", call.recoveryAttempts(),
                            "compensationAttempted", call.compensationAttempted(), "elapsedMillis", call.elapsedMillis()));
            if (call.result().isSuccess()) return IntentResult.success(intent, call.result().getOutput());
            return IntentResult.failed(intent, call.result().getMessage());
        }
        if (planner == null) return IntentResult.failed(intent, "未注册意图 Loop 规划器");
        Instant deadline = Instant.now().plus(MAX_INTENT_DURATION);
        List<AgentObservation> observations = new ArrayList<>();
        List<ConversationMessage> history = loadHistory(intent, context);
        publish(context, intent, AiEventType.HISTORY_LOADED, "已按当前意图加载 " + history.size() + " 条历史消息", 25,
                java.util.Map.of("historyCount", history.size(), "requested", intent.getContextRequirement().getHistoryLimit()));
        List<CapabilityDefinition> eligibleCapabilities = capabilityResolver.resolve(intent, context, capabilities);
        if (eligibleCapabilities.isEmpty()) return IntentResult.failed(intent, "当前意图没有注册可用能力");
        int recoveryAttempts = 0;
        int toolCalls = 0;
        String lastFailureSignature = null;
        for (int iteration = 0; iteration < MAX_ITERATIONS && Instant.now().isBefore(deadline); iteration++) {
            log.info("AI 开始规划 executionId={}, intentId={}, iteration={}", context.getConversation().getExecutionId(), intent.getId(), iteration + 1);
            publish(context, intent, AiEventType.AGENT_PLANNING,
                    iteration == 0 ? "正在判断完成任务需要哪些工具" : "正在根据已有结果规划下一步", 35,
                    payload("iteration", iteration + 1, "objective", task.getObjective()));
            IntentLoopRequest request = new IntentLoopRequest();
            request.setConversation(context.getConversation());
            request.setIntent(intent);
            request.setHistory(history);
            java.util.Map<String, IntentResult> dependencies = new java.util.LinkedHashMap<>();
            for (String id : intent.getDependsOn()) {
                if (context.getResults().containsKey(id)) dependencies.put(id, context.getResults().get(id));
            }
            request.setPreviousIntentResults(dependencies);
            request.setObservations(List.copyOf(observations));
            context.setCurrentTaskObservation(observations.isEmpty() ? null
                    : IntentResult.success(intent, observations.get(observations.size() - 1).getResult().getOutput()));
            List<CapabilityDefinition> iterationCapabilities = capabilityCandidateSelector.select(intent, context,
                    eligibleCapabilities);
            if (iterationCapabilities == null || iterationCapabilities.isEmpty())
                return IntentResult.failed(intent, "当前意图没有符合宿主策略的候选能力");
            log.info("AI 意图本轮可用工具 executionId={}, intentId={}, iteration={}, capabilities={}",
                    context.getConversation().getExecutionId(), intent.getId(), iteration + 1,
                    iterationCapabilities.stream().map(CapabilityDefinition::getCode).toList());
            Set<String> allowedCodes = iterationCapabilities.stream().map(CapabilityDefinition::getCode)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            request.setCapabilities(iterationCapabilities);
            request.setIteration(iteration);
            ContextSnapshot planningContext = contextSnapshot(ContextPhase.TASK_PLANNING, context, intent, task,
                    dependencies, observations, iterationCapabilities, null);
            request.setContextSnapshot(planningContext);
            publishContext(context, intent, planningContext);
            IntentLoopDecision decision;
            try {
                counts[0]++;
                decision = decide(request, deadline);
            } catch (RuntimeException exception) {
                return IntentResult.failed(intent, exception.getMessage());
            }
            if (decision == null) return IntentResult.failed(intent, "Loop 规划器未返回决策");
            if (decision.getStatus() == null) return IntentResult.failed(intent, "Loop 规划器未返回有效状态");
            log.info("AI 规划结果 executionId={}, intentId={}, iteration={}, status={}, toolCount={}",
                    context.getConversation().getExecutionId(), intent.getId(), iteration + 1, decision.getStatus(),
                    decision.getToolInvocations() == null ? 0 : decision.getToolInvocations().size());
            publish(context, intent, AiEventType.AGENT_DECISION, decisionMessage(decision), 45,
                    java.util.Map.of("iteration", iteration + 1, "decision", decision));
            if (decision.getStatus() == IntentLoopDecision.Status.COMPLETED) {
                boolean hasSuccessfulDependency = !dependencies.isEmpty() && dependencies.values().stream()
                        .allMatch(result -> result != null && result.getStatus() == IntentStatus.SUCCESS);
                boolean hasHistoryEvidence = !history.isEmpty()
                        && decision.getResult() != null && !decision.getResult().isBlank();
                if (observations.isEmpty() && !hasSuccessfulDependency && !hasHistoryEvidence) {
                    return IntentResult.failed(intent, "缺少成功工具调用或前置结果，不能直接声明任务已完成");
                }
                if (!observations.isEmpty() && !observations.get(observations.size() - 1).getResult().isSuccess()) {
                    return IntentResult.failed(intent, "最近一次工具调用失败，不能直接声明完成");
                }
                if (iteration > 0) counts[2] = 1;
                return IntentResult.success(intent, decision.getResult());
            }
            if (decision.getStatus() == IntentLoopDecision.Status.UNRESOLVABLE) {
                return new IntentResult(intent.getId(), intent.getCode(), IntentStatus.FAILED,
                        null, decision.getResult() != null && !decision.getResult().isBlank()
                        ? decision.getResult() : decision.getReason() == null ? "意图无法解决" : decision.getReason());
            }
            if (decision.getStatus() == IntentLoopDecision.Status.WAITING_USER) {
                String question = decision.getResult() == null || decision.getResult().isBlank()
                        ? decision.getReason() : decision.getResult();
                return IntentResult.waitingUser(intent,
                        question == null || question.isBlank() ? "需要补充信息后才能继续" : question);
            }
            if (decision.getToolInvocations() == null || decision.getToolInvocations().isEmpty()) {
                return IntentResult.failed(intent, "Loop 未返回工具调用方案");
            }
            boolean allTerminal = true;
            boolean allSuccessful = true;
            java.util.List<Object> outputs = new java.util.ArrayList<>();
            for (CapabilityInvocation invocation : orderInvocations(decision.getToolInvocations())) {
                if (!allSuccessful) break;
                if (++toolCalls > MAX_TOOL_CALLS) return IntentResult.failed(intent, "意图已达到最大工具调用次数");
                if (invocation == null || invocation.getCapabilityCode() == null
                        || !allowedCodes.contains(invocation.getCapabilityCode())) {
                    return IntentResult.failed(intent, "Loop 返回了当前意图不允许使用的能力");
                }
                CapabilityDefinition definition = capabilities.definition(invocation.getCapabilityCode());
                try {
                    context.setCurrentTaskObservation(observations.isEmpty() ? null
                            : IntentResult.success(intent, observations.get(observations.size() - 1).getResult().getOutput()));
                    invocation = invocationBinder.bind(intent, context, invocation, definition);
                    task.setCapabilityInvocation(invocation);
                } catch (IllegalArgumentException exception) {
                    log.warn("AI 能力输入绑定失败 executionId={}, intentId={}, capability={}, message={}",
                            context.getConversation().getExecutionId(), intent.getId(),
                            invocation == null ? null : invocation.getCapabilityCode(), exception.getMessage());
                    return IntentResult.failed(intent, exception.getMessage());
                }
                String boundCapabilityCode = invocation.getCapabilityCode();
                java.util.Map<String, Object> boundArguments = invocation.getArguments();
                boolean duplicate = observations.stream().anyMatch(observation ->
                        boundCapabilityCode.equals(observation.getToolCode())
                                && java.util.Objects.equals(boundArguments, observation.getArguments()));
                if (duplicate) return finalizeObservations(intent, context, history, observations, counts,
                        "检测到重复能力调用，停止继续探索");
                if (invocation.getInputRefs() != null && !invocation.getInputRefs().isEmpty()) {
                    publish(context, intent, AiEventType.INPUT_BOUND, "已绑定前置意图结果", 50,
                            java.util.Map.of("capabilityCode", invocation.getCapabilityCode(),
                                    "references", invocation.getInputRefs()));
                }
                publishToolCalling(context, intent, invocation, definition);
                CapabilityCallResult call = capabilityInvoker.invoke(intent, context, invocation,
                        MAX_FAILURE_RECOVERY - recoveryAttempts);
                counts[1]++;
                CapabilityResult result = call.result();
                log.info("AI 工具调用完成 executionId={}, intentId={}, capability={}, success={}, recoveries={}",
                        context.getConversation().getExecutionId(), intent.getId(), invocation.getCapabilityCode(),
                        result.isSuccess(), call.recoveryAttempts());
                recoveryAttempts += call.recoveryAttempts();
                String actualCapabilityCode = call.actualCapabilityCode() == null
                        ? invocation.getCapabilityCode() : call.actualCapabilityCode();
                observations.add(new AgentObservation(actualCapabilityCode, invocation.getCapabilityCode(),
                        invocation.getArguments(), result, call.recoveryAttempts(),
                        call.compensationAttempted(), call.elapsedMillis()));
                publish(context, intent, AiEventType.TOOL_RESULT,
                        toolResultMessage(definition, result), 65,
                        payload("requestedCapabilityCode", invocation.getCapabilityCode(),
                                "capabilityCode", actualCapabilityCode,
                                "capabilityDescription", definition == null ? null : definition.getDescription(),
                                "arguments", invocation.getArguments(), "result", result, "recoveryAttempts", call.recoveryAttempts(),
                                "compensationAttempted", call.compensationAttempted(),
                                "elapsedMillis", call.elapsedMillis()));
                allTerminal &= definition != null && definition.isTerminalResult();
                allSuccessful &= result.isSuccess();
                if (result.isSuccess()) outputs.add(result.getOutput());
                if (!result.isSuccess()) {
                    String failureSignature = invocation.getCapabilityCode() + "|"
                            + String.valueOf(invocation.getArguments()) + "|" + result.getErrorCode();
                    if (!result.isRetryable() && failureSignature.equals(lastFailureSignature)) {
                        return IntentResult.failed(intent, result.getMessage());
                    }
                    lastFailureSignature = failureSignature;
                    recoveryAttempts++;
                    if (recoveryAttempts >= MAX_FAILURE_RECOVERY) {
                        return IntentResult.failed(intent, "能力连续失败，已达到最多 3 次恢复限制");
                    }
                }
            }
            if (allTerminal && allSuccessful && (decision.getToolInvocations().size() == 1 || decision.isCompleteAfterTools())) {
                return IntentResult.success(intent, outputCombiner.combine(intent, outputs));
            }
        }
        return finalizeObservations(intent, context, history, observations, counts,
                Instant.now().isAfter(deadline) ? "已达到意图执行时间边界" : "已达到最大规划轮次");
    }

    /** 对同一意图内的工具调用按显式 invocationId 依赖排序，保持无依赖调用的原始顺序。 */
    private List<CapabilityInvocation> orderInvocations(List<CapabilityInvocation> invocations) {
        if (invocations == null || invocations.size() < 2) return invocations == null ? List.of() : invocations;
        java.util.Map<String, CapabilityInvocation> byId = new java.util.LinkedHashMap<>();
        for (CapabilityInvocation invocation : invocations) {
            if (invocation != null && invocation.getInvocationId() != null && !invocation.getInvocationId().isBlank()) {
                if (byId.put(invocation.getInvocationId(), invocation) != null) throw new IllegalArgumentException("工具调用 ID 重复");
            }
        }
        if (byId.isEmpty()) return invocations;
        List<CapabilityInvocation> result = new java.util.ArrayList<>();
        java.util.Set<String> visiting = new java.util.HashSet<>();
        java.util.Set<String> visited = new java.util.HashSet<>();
        for (CapabilityInvocation invocation : invocations) visitInvocation(invocation, byId, visiting, visited, result);
        return result;
    }

    private void visitInvocation(CapabilityInvocation invocation, java.util.Map<String, CapabilityInvocation> byId,
                                 java.util.Set<String> visiting, java.util.Set<String> visited,
                                 List<CapabilityInvocation> result) {
        String id = invocation == null ? null : invocation.getInvocationId();
        if (id == null || id.isBlank() || visited.contains(id)) { if (id == null || id.isBlank()) result.add(invocation); return; }
        if (!visiting.add(id)) throw new IllegalArgumentException("工具调用依赖存在环: " + id);
        for (String dependency : invocation.getDependsOnInvocationIds() == null ? List.<String>of() : invocation.getDependsOnInvocationIds()) {
            CapabilityInvocation parent = byId.get(dependency);
            if (parent == null) throw new IllegalArgumentException("工具调用依赖不存在: " + dependency);
            visitInvocation(parent, byId, visiting, visited, result);
        }
        visiting.remove(id); visited.add(id); result.add(invocation);
    }

    private IntentResult finalizeObservations(IntentCandidate intent, AiExecutionContext context,
                                              List<ConversationMessage> history,
                                              List<AgentObservation> observations, int[] counts, String reason) {
        List<AgentObservation> successful = observations.stream()
                .filter(item -> item.getResult() != null && item.getResult().isSuccess()).toList();
        if (successful.isEmpty()) return IntentResult.failed(intent, reason + "，且没有可用于归纳的成功结果");
        publish(context, intent, AiEventType.AGENT_PLANNING, reason + "，正在基于已有真实结果归纳", 72,
                payload("finalizing", true, "observationCount", successful.size()));
        IntentLoopRequest request = new IntentLoopRequest();
        request.setConversation(context.getConversation());
        request.setIntent(intent);
        request.setHistory(history);
        java.util.Map<String, IntentResult> dependencies = new java.util.LinkedHashMap<>();
        for (String id : intent.getDependsOn()) {
            if (context.getResults().containsKey(id)) dependencies.put(id, context.getResults().get(id));
        }
        request.setPreviousIntentResults(dependencies);
        request.setObservations(successful);
        request.setCapabilities(List.of());
        request.setIteration(MAX_ITERATIONS);
        request.setFinalizing(true);
        ContextSnapshot summaryContext = contextSnapshot(ContextPhase.RESULT_SUMMARY, context, intent, null,
                dependencies, successful, List.of(), null);
        request.setContextSnapshot(summaryContext);
        publishContext(context, intent, summaryContext);
        try {
            counts[0]++;
            IntentLoopDecision decision = decide(request, Instant.now().plusSeconds(30));
            publish(context, intent, AiEventType.AGENT_DECISION, decisionMessage(decision), 75,
                    java.util.Map.of("iteration", MAX_ITERATIONS + 1, "finalizing", true, "decision", decision));
            if (decision != null && decision.getStatus() == IntentLoopDecision.Status.COMPLETED
                    && decision.getResult() != null && !decision.getResult().isBlank())
                return IntentResult.success(intent, decision.getResult());
            String message = decision != null && decision.getReason() != null && !decision.getReason().isBlank()
                    ? decision.getReason() : reason + "，已有结果不足以满足任务目标";
            return IntentResult.failed(intent, message);
        } catch (RuntimeException exception) {
            return IntentResult.failed(intent, "最终结果归纳失败: " + exception.getMessage());
        }
    }

    private ContextSnapshot contextSnapshot(ContextPhase phase, AiExecutionContext context, IntentCandidate intent,
                                            Task task, Map<String, IntentResult> dependencies,
                                            List<AgentObservation> observations,
                                            List<CapabilityDefinition> capabilities,
                                            TaskVerification verification) {
        ContextRequest request = new ContextRequest();
        request.setPhase(phase);
        request.setConversation(context.getConversation());
        request.setIntent(intent);
        request.setTask(task);
        request.setPreviousIntentResults(dependencies == null ? Map.of() : dependencies);
        request.setObservations(observations == null ? List.of() : observations);
        request.setCapabilities(capabilities == null ? List.of() : capabilities);
        request.setVerification(verification);
        return contextAssembler.assemble(request);
    }

    private void publishContext(AiExecutionContext context, IntentCandidate intent, ContextSnapshot snapshot) {
        publish(context, intent, AiEventType.CONTEXT_ASSEMBLED,
                "已为当前阶段构造受控上下文", 34,
                payload("phase", snapshot.getPhase(), "itemCount", snapshot.getItems().size(),
                        "estimatedTokens", snapshot.getEstimatedTokens(), "truncatedItems", snapshot.getTruncatedItems()));
        if (snapshot.getTruncatedItems() > 0) publish(context, intent, AiEventType.CONTEXT_TRUNCATED,
                "上下文超出预算，已保留高优先级证据并裁剪其余内容", 34,
                payload("phase", snapshot.getPhase(), "truncatedItems", snapshot.getTruncatedItems()));
    }

    private void publish(AiExecutionContext context, IntentCandidate intent, AiEventType type,
                         String message, int progress, java.util.Map<String, Object> payload) {
        ConversationContext conversation = context.getConversation();
        events.publish(new AiEvent(java.util.UUID.randomUUID().toString(), conversation.getConversationId(),
                conversation.getExecutionId(), intent.getId(), type, message, progress, payload, null));
    }

    private void publishToolCalling(AiExecutionContext context, IntentCandidate intent, CapabilityInvocation invocation,
                                    CapabilityDefinition definition) {
        String description = definition == null ? invocation.getCapabilityCode() : definition.getDescription();
        publish(context, intent, AiEventType.TOOL_CALLING, "选择工具：" + displayText(description, invocation.getCapabilityCode()), 55,
                payload("capabilityCode", invocation.getCapabilityCode(), "capabilityDescription", description,
                        "arguments", invocation.getArguments(), "argumentSummary", argumentSummary(invocation.getArguments())));
    }

    private String decisionMessage(IntentLoopDecision decision) {
        if (decision.getStatus() == IntentLoopDecision.Status.COMPLETED) return "现有信息已满足任务目标，准备整理结果";
        if (decision.getStatus() == IntentLoopDecision.Status.WAITING_USER) return "需要用户补充或确认信息";
        if (decision.getStatus() == IntentLoopDecision.Status.UNRESOLVABLE) return "当前信息不足，无法继续完成任务";
        int count = decision.getToolInvocations() == null ? 0 : decision.getToolInvocations().size();
        return count > 0 ? "已规划下一步，将执行 " + count + " 个工具调用" : "已规划下一步处理方式";
    }

    private String toolResultMessage(CapabilityDefinition definition, CapabilityResult result) {
        String name = definition == null ? "工具" : displayText(definition.getDescription(), definition.getCode());
        return result.isSuccess() ? name + "执行完成" : name + "执行失败：" + displayText(result.getMessage(), "未返回失败原因");
    }

    private String argumentSummary(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return "无需额外参数";
        return arguments.entrySet().stream().map(entry -> entry.getKey() + "=" + abbreviate(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("；"));
    }

    private String abbreviate(Object value) {
        String text = java.util.Objects.toString(value, "");
        return text.length() > 120 ? text.substring(0, 117) + "..." : text;
    }

    private String displayText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            if (values[index] != null && values[index + 1] != null) result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private IntentLoopDecision decide(IntentLoopRequest request, Instant deadline) {
        long remainingMillis = Duration.between(Instant.now(), deadline).toMillis();
        if (remainingMillis <= 0) return null;
        long timeoutMillis = Math.min(remainingMillis, MAX_PLANNER_DURATION.toMillis());
        Future<IntentLoopDecision> future = PLANNER_EXECUTOR.submit(() -> planner.decide(request));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException("意图 Loop 规划调用超时", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("意图 Loop 规划调用被中断", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("意图 Loop 规划调用失败", exception.getCause());
        }
    }

    private List<ConversationMessage> loadHistory(IntentCandidate intent, AiExecutionContext context) {
        ContextRequirement requirement = intent.getContextRequirement();
        boolean historyRequested = requirement.isRecallHistory()
                || requirement.getScopes().contains("CONVERSATION_HISTORY");
        if (!historyRequested || context.getConversation() == null) {
            return List.of();
        }
        int limit = Math.min(requirement.getHistoryLimit() > 0 ? requirement.getHistoryLimit() : 20, 50);
        List<ConversationMessage> existing = context.getConversation().getHistory();
        if (existing != null && existing.size() >= limit) {
            return existing.subList(Math.max(0, existing.size() - limit), existing.size());
        }
        if (historyStore == null || context.getConversation().getConversationId() == null) return existing == null ? List.of() : existing;
        List<ConversationMessage> loaded = new ArrayList<>(historyStore.loadBefore(
                context.getConversation().getConversationId(), limit,
                context.getConversation().getHistoryBefore() <= 0 ? Long.MAX_VALUE : context.getConversation().getHistoryBefore()));
        return loaded.size() > limit ? loaded.subList(loaded.size() - limit, loaded.size()) : loaded;
    }
}
