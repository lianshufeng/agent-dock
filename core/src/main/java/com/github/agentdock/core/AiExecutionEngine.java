package com.github.agentdock.core;

import com.github.agentdock.core.aggregation.ResultAggregator;
import com.github.agentdock.core.aggregation.ResultSummarizer;
import com.github.agentdock.core.event.*;
import com.github.agentdock.core.loop.IntentLoopExecutor;
import com.github.agentdock.core.intent.IntentFactory;
import com.github.agentdock.core.internal.ExecutorSupport;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.store.ChatHistoryStore;
import com.github.agentdock.core.store.ExecutionCheckpointStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.agentdock.core.type.*;
import com.github.agentdock.core.routing.ExecutionRouter;
import com.github.agentdock.core.routing.impl.DefaultExecutionRouter;
import com.github.agentdock.core.planning.*;
import com.github.agentdock.core.steering.*;
import com.github.agentdock.core.context.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** AI 核心 DAG 编排器，不依赖任何具体业务 Service 或 DAO。 */
public class AiExecutionEngine {
    private static final Logger log = LoggerFactory.getLogger(AiExecutionEngine.class);
    /** 意图分析默认携带的最近上下文条数，避免省略表达失去语境。 */
    private static final int ANALYSIS_HISTORY_LIMIT = 6;
    private static final int CPU_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int DAG_PARALLELISM = Math.max(1, CPU_COUNT / 2);
    private static final int MAX_EXECUTION_CONCURRENCY = Math.max(1, Math.min(4, DAG_PARALLELISM));
    private static final ExecutorService DAG_EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(
            DAG_PARALLELISM, DAG_PARALLELISM,
            0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(Math.max(8, Runtime.getRuntime().availableProcessors() * 4)),
            ExecutorSupport.daemonThreadFactory("ai-dag-worker"),
            new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DAG_EXECUTOR::shutdownNow, "ai-dag-shutdown"));
    }
    /** 以下组件均通过构造方法传入，执行引擎不负责容器发现或业务实例化。 */
    private final IntentFactory intentFactory;
    private final IntentLoopExecutor intentLoopExecutor;
    private final ChatHistoryStore chatHistoryStore;
    private final ExecutionCheckpointStore executionCheckpointStore;
    private final ObjectMapper checkpointMapper = new ObjectMapper().findAndRegisterModules();
    private final ResultAggregator aggregator;
    private final ResultSummarizer summarizer;
    private final AiEventPublisher events;
    private final ExecutionRouter executionRouter = new DefaultExecutionRouter();
    private final AiExecutionMode executionMode;
    private final PlanReplanner planReplanner;
    private final ContextAssembler contextAssembler;
    private final SteeringPlanMode steeringPlanMode;
    private final PlanPatchApplier planPatchApplier = new PlanPatchApplier();
    private static final int MAX_PLAN_VERSIONS = 4;

    public AiExecutionEngine(IntentFactory intentFactory, ResultAggregator aggregator, ResultSummarizer summarizer,
                             AiEventPublisher events, IntentLoopExecutor intentLoopExecutor,
                             ChatHistoryStore chatHistoryStore) {
        this(intentFactory, aggregator, summarizer, events, intentLoopExecutor, chatHistoryStore, AiExecutionMode.SERIAL);
    }

    public AiExecutionEngine(IntentFactory intentFactory, ResultAggregator aggregator, ResultSummarizer summarizer,
                             AiEventPublisher events, IntentLoopExecutor intentLoopExecutor,
                             ChatHistoryStore chatHistoryStore, AiExecutionMode executionMode) {
        this(intentFactory, aggregator, summarizer, events, intentLoopExecutor, chatHistoryStore, executionMode,
                new NoopPlanReplanner(), new DefaultContextAssembler());
    }

    public AiExecutionEngine(IntentFactory intentFactory, ResultAggregator aggregator, ResultSummarizer summarizer,
                             AiEventPublisher events, IntentLoopExecutor intentLoopExecutor,
                             ChatHistoryStore chatHistoryStore, AiExecutionMode executionMode,
                             PlanReplanner planReplanner, ContextAssembler contextAssembler) {
        this(intentFactory, aggregator, summarizer, events, intentLoopExecutor, chatHistoryStore,
                executionMode, planReplanner, contextAssembler, SteeringPlanMode.CONSERVATIVE);
    }

    public AiExecutionEngine(IntentFactory intentFactory, ResultAggregator aggregator, ResultSummarizer summarizer,
                             AiEventPublisher events, IntentLoopExecutor intentLoopExecutor,
                             ChatHistoryStore chatHistoryStore, AiExecutionMode executionMode,
                             PlanReplanner planReplanner, ContextAssembler contextAssembler,
                             SteeringPlanMode steeringPlanMode) {
        this(intentFactory, aggregator, summarizer, events, intentLoopExecutor, chatHistoryStore,
                executionMode, planReplanner, contextAssembler, steeringPlanMode, null);
    }

    public AiExecutionEngine(IntentFactory intentFactory, ResultAggregator aggregator, ResultSummarizer summarizer,
                             AiEventPublisher events, IntentLoopExecutor intentLoopExecutor,
                             ChatHistoryStore chatHistoryStore, AiExecutionMode executionMode,
                             PlanReplanner planReplanner, ContextAssembler contextAssembler,
                             SteeringPlanMode steeringPlanMode, ExecutionCheckpointStore executionCheckpointStore) {
        this.intentFactory = intentFactory;
        this.aggregator = aggregator;
        this.summarizer = summarizer;
        this.events = events;
        this.intentLoopExecutor = intentLoopExecutor;
        this.chatHistoryStore = chatHistoryStore;
        this.executionCheckpointStore = executionCheckpointStore;
        this.executionMode = executionMode == null ? AiExecutionMode.SERIAL : executionMode;
        this.planReplanner = planReplanner == null ? new NoopPlanReplanner() : planReplanner;
        this.contextAssembler = contextAssembler == null ? new DefaultContextAssembler() : contextAssembler;
        this.steeringPlanMode = steeringPlanMode == null ? SteeringPlanMode.CONSERVATIVE : steeringPlanMode;
    }

    /** 按已经排序的意图队列串行执行，并持续发布可供 SSE 转发的状态事件。 */
    public ConversationResult execute(ConversationContext conversation) {
        return execute(conversation, ExecutionSteering.disabled());
    }

    /** 按计划执行，并在批次边界消费宿主投递的补充输入。 */
    public ConversationResult execute(ConversationContext conversation, ExecutionSteering steering) {
        if (conversation == null) throw new IllegalArgumentException("会话上下文不能为空");
        steering = steering == null ? ExecutionSteering.disabled() : steering;
        if (conversation.getExecutionId() == null || conversation.getExecutionId().isBlank()) {
            conversation.setExecutionId(UUID.randomUUID().toString());
        }
        log.info("AI 执行开始 executionId={}, conversationId={}, inputLength={}", conversation.getExecutionId(),
                conversation.getConversationId(), conversation.getUserInput() == null ? 0 : conversation.getUserInput().length());
        ExecutionSnapshot saved = loadCheckpoint(conversation);
        if (saved != null && saved.getFinalResult() != null) return saved.getFinalResult();
        AiExecutionContext context = new AiExecutionContext(conversation);
        List<IntentResult> results;
        ExecutionPlan plan;
        if (saved == null) {
            preloadAnalysisHistory(conversation);
            appendUser(conversation);
            publish(conversation, null, AiEventType.CONVERSATION_STARTED, "开始处理", 0);
            IntentAnalysis analysis;
            try {
                analysis = intentFactory.analyze(conversation);
            } catch (RuntimeException exception) {
                log.error("AI 意图识别失败 executionId={}: {}", conversation.getExecutionId(), exception.getMessage(), exception);
                ConversationResult failed = new ConversationResult(IntentStatus.FAILED, List.of(), exception.getMessage());
                appendAssistant(conversation, failed.getMessage());
                publish(conversation, null, AiEventType.FINAL_RESULT, failed.getMessage(), 100);
                return failed;
            }
            log.info("AI 意图识别完成 executionId={}, count={}, intents={}", conversation.getExecutionId(),
                    analysis.getOrderedIntents().size(), analysis.getOrderedIntents().stream()
                            .map(intent -> intent.getId() + ":" + intent.getCode()).toList());
            loadRequestedHistory(conversation, analysis.getContextRequirement());
            publish(conversation, null, AiEventType.INTENT_ANALYZING, "需求理解完成，共识别到 " + analysis.getOrderedIntents().size() + " 个任务", 10);
            publish(conversation, null, AiEventType.INTENT_DETECTED, "识别到需求：" + intentSummary(analysis.getOrderedIntents()), 12,
                    java.util.Map.of("intents", analysis.getOrderedIntents()));
            publish(conversation, null, AiEventType.QUEUE_CREATED, "已构建任务依赖图，将按依赖就绪并行执行：" + intentSummary(analysis.getOrderedIntents()), 15,
                    java.util.Map.of("intentIds", analysis.getOrderedIntents().stream().map(IntentCandidate::getId).toList(),
                            "tasks", analysis.getOrderedIntents().stream().map(IntentCandidate::getDescription).toList()));
            if (analysis.requiresClarification()) {
                publish(conversation, null, AiEventType.WAITING_USER, analysis.getClarificationQuestion(), 10);
                ConversationResult result = new ConversationResult(IntentStatus.WAITING_USER, List.of(), analysis.getClarificationQuestion());
                appendAssistant(conversation, result.getMessage());
                return result;
            }
            if (analysis.getOrderedIntents().isEmpty()) {
                ConversationResult result = new ConversationResult(IntentStatus.FAILED, List.of(), "未识别出可执行意图");
                appendAssistant(conversation, result.getMessage());
                publish(conversation, null, AiEventType.FINAL_RESULT, result.getMessage(), 100);
                return result;
            }
            results = new ArrayList<>();
            plan = ExecutionPlan.from(conversation.getExecutionId(), analysis.getOrderedIntents());
            saveCheckpoint(conversation, plan, results, null);
        } else {
            plan = saved.getPlan();
            results = new ArrayList<>(saved.getResults() == null ? List.of() : saved.getResults());
            results.forEach(context::addResult);
            for (PlanNode node : plan.getNodes().values()) {
                if (node.getStatus() != PlanNodeStatus.RUNNING) continue;
                node.setStatus(PlanNodeStatus.WAITING_USER);
                IntentResult uncertain = IntentResult.waitingUser(node.getIntent(),
                        "执行在工具调用期间中断，需确认外部操作结果后继续");
                context.addResult(uncertain);
                replaceResult(results, uncertain);
            }
            saveCheckpoint(conversation, plan, results, null);
        }
        while (true) {
            SteeringBatch steeringBatch = steering.drain();
            if (!steeringBatch.isEmpty()) {
                applySteering(steeringBatch, plan, context, results, conversation);
                saveCheckpoint(conversation, plan, results, null);
            }
            if (plan.pendingNodes().isEmpty()) {
                if (steering.sealIfEmpty()) break;
                continue;
            }
            List<PlanNode> readyNodes = plan.readyNodes().stream()
                    .limit(executionMode == AiExecutionMode.PARALLEL ? MAX_EXECUTION_CONCURRENCY : 1)
                    .toList();
            if (readyNodes.isEmpty()) {
                PlanNode deadlocked = plan.pendingNodes().get(0);
                deadlocked.setStatus(PlanNodeStatus.FAILED);
                IntentResult failed = IntentResult.failed(deadlocked.getIntent(), "执行计划没有可运行节点");
                context.addResult(failed);
                replaceResult(results, failed);
                saveCheckpoint(conversation, plan, results, null);
                continue;
            }
            Map<String, CompletableFuture<IntentResult>> running = new LinkedHashMap<>();
            for (PlanNode node : readyNodes) {
                node.setStatus(PlanNodeStatus.RUNNING);
            }
            // 先持久化不确定状态，再允许任何可能有外部副作用的工具启动。
            saveCheckpoint(conversation, plan, results, null);
            for (PlanNode node : readyNodes) {
                IntentCandidate intent = node.getIntent();
                try {
                    running.put(intent.getId(), CompletableFuture.supplyAsync(() ->
                            executeIntent(intent, plan.orderedIntents().size(), context, results.size()), DAG_EXECUTOR));
                } catch (RejectedExecutionException exception) {
                    IntentResult rejected = IntentResult.failed(intent, "AI 执行资源已满，请稍后重试");
                    context.addResult(rejected);
                    replaceResult(results, rejected);
                    node.setStatus(PlanNodeStatus.FAILED);
                }
            }
            saveCheckpoint(conversation, plan, results, null);
            running.forEach((id, future) -> {
                IntentResult result;
                try {
                    result = future.join();
                } catch (RuntimeException exception) {
                    IntentCandidate intent = plan.getNodes().get(id) == null ? null : plan.getNodes().get(id).getIntent();
                    result = intent == null ? null : IntentResult.failed(intent, exception.getMessage());
                }
                if (result != null) {
                    context.addResult(result);
                    replaceResult(results, result);
                    PlanNode node = plan.getNodes().get(id);
                    node.setStatus(toNodeStatus(result));
                    attemptReplan(plan, node, result, context, results);
                    saveCheckpoint(conversation, plan, results, null);
                }
            });
        }
        results.sort(java.util.Comparator.comparingInt(result -> plan.orderedIntents().stream()
                .map(IntentCandidate::getId).toList().indexOf(result.getIntentId())));
        ConversationResult finalResult = aggregator.aggregate(results);
        finalResult.setMessage(summarizer.summarize(conversation, finalResult));
        log.info("AI 执行结束 executionId={}, status={}, resultCount={}, message={}", conversation.getExecutionId(),
                finalResult.getStatus(), results.size(), finalResult.getMessage());
        publish(conversation, null, AiEventType.FINAL_RESULT, finalResult.getMessage(), 100);
        appendAssistant(conversation, finalResult.getMessage());
        saveCheckpoint(conversation, plan, results, finalResult);
        return finalResult;
    }

    private void applySteering(SteeringBatch batch, ExecutionPlan plan, AiExecutionContext context,
                               List<IntentResult> results, ConversationContext conversation) {
        appendSteering(conversation, batch);
        String previousInput = conversation.getUserInput();
        conversation.setUserInput(joinInputs(null, batch));
        if (chatHistoryStore != null && conversation.getConversationId() != null) {
            conversation.setHistory(chatHistoryStore.load(conversation.getConversationId(), 50));
        }
        publish(conversation, null, AiEventType.STEERING_RECEIVED,
                "已接收 " + batch.inputs().size() + " 条补充要求", 15,
                Map.of("inputIds", inputIds(batch), "planVersion", plan.getVersion()));
        publish(conversation, null, AiEventType.STEERING_ANALYZING, "正在根据补充要求调整执行计划", 16,
                Map.of("inputIds", inputIds(batch), "planVersion", plan.getVersion()));
        SteeringPlanDecision decision;
        try {
            if (steeringPlanMode == SteeringPlanMode.LEGACY_REPLACE_PENDING) {
                IntentAnalysis analysis = intentFactory.analyze(conversation, plan, context.getResults());
                decision = new SteeringPlanDecision(SteeringAction.ADD, List.of(),
                        analysis.getOrderedIntents(), "旧覆盖策略", analysis.getClarificationQuestion());
            } else {
                decision = intentFactory.analyzeSteering(conversation, plan, context.getResults());
            }
        } catch (RuntimeException exception) {
            try {
                IntentAnalysis fallback = intentFactory.analyze(conversation, plan, context.getResults());
                List<IntentCandidate> fresh = fallback.getOrderedIntents().stream().filter(candidate ->
                        plan.getNodes().values().stream().noneMatch(existing ->
                                existing.getIntent().getCode().equals(candidate.getCode())
                                        && java.util.Objects.equals(existing.getIntent().getDescription(),
                                        candidate.getDescription()))).toList();
                if (fresh.isEmpty() && !fallback.requiresClarification())
                    throw new IllegalArgumentException("降级分析未识别出不同于原计划的新意图");
                decision = new SteeringPlanDecision(SteeringAction.ADD, List.of(),
                        fresh, "计划变更判断失败，安全降级为追加", fallback.getClarificationQuestion());
            } catch (RuntimeException fallbackFailure) {
                conversation.setUserInput(joinInputs(previousInput, batch));
                publish(conversation, null, AiEventType.STEERING_REJECTED, "补充要求分析失败，原计划继续执行", 16,
                        Map.of("inputIds", inputIds(batch),
                                "reason", displayText(fallbackFailure.getMessage(), "意图分析失败")));
                return;
            }
        }
        // 分类只看本次补充；执行阶段需同时保留原任务与新增约束，避免原意图失去原始输入。
        conversation.setUserInput(joinInputs(previousInput, batch));
        if (decision.requiresClarification()) {
            publish(conversation, null, AiEventType.STEERING_REJECTED, decision.clarificationQuestion(), 16,
                    Map.of("inputIds", inputIds(batch), "reason", "WAITING_USER"));
            publish(conversation, null, AiEventType.WAITING_USER,
                    "补充要求需澄清：" + decision.clarificationQuestion(), 16);
            return;
        }
        if (decision.action() == SteeringAction.NOOP) {
            publish(conversation, null, AiEventType.STEERING_APPLIED, "补充要求与已接收内容重复，继续原计划", 19,
                    Map.of("inputIds", inputIds(batch), "planVersion", plan.getVersion(), "operation", "NOOP"));
            return;
        }
        if (decision.action() != SteeringAction.CANCEL && decision.intents().isEmpty()) {
            publish(conversation, null, AiEventType.STEERING_REJECTED, "补充要求未识别出可执行意图", 16,
                    Map.of("inputIds", inputIds(batch), "reason", "NO_INTENT"));
            return;
        }

        int previousVersion = plan.getVersion();
        PlanPatch patch;
        try {
            if (steeringPlanMode == SteeringPlanMode.LEGACY_REPLACE_PENDING) {
                patch = legacySteeringPatch(decision, plan, previousVersion + 1);
            } else {
                try {
                    patch = steeringPatch(decision, plan, previousVersion + 1);
                } catch (IllegalArgumentException invalidDecision) {
                    if (decision.action() == SteeringAction.ADD || decision.intents().isEmpty()) throw invalidDecision;
                    decision = new SteeringPlanDecision(SteeringAction.ADD, List.of(), decision.intents(),
                            "目标节点无效，安全降级为追加", null);
                    patch = steeringPatch(decision, plan, previousVersion + 1);
                }
            }
            planPatchApplier.apply(plan, patch,
                    intentFactory.intentDefinitions().stream().map(IntentDefinition::getCode)
                            .collect(java.util.stream.Collectors.toSet()), Integer.MAX_VALUE, false);
        } catch (IllegalArgumentException exception) {
            publish(conversation, null, AiEventType.STEERING_REJECTED, "补充要求无法应用到当前计划", 16,
                    Map.of("inputIds", inputIds(batch), "reason", exception.getMessage()));
            return;
        }
        List<String> cancelledIds = patch.getOperations().stream()
                .filter(operation -> operation.getType() == PlanPatchType.CANCEL_NODE)
                .map(PlanPatchOperation::getTargetNodeId).toList();
        List<String> replacedIds = patch.getOperations().stream()
                .filter(operation -> operation.getType() == PlanPatchType.REPLACE_NODE)
                .map(PlanPatchOperation::getTargetNodeId).toList();
        List<String> addedIds = patch.getOperations().stream()
                .filter(operation -> operation.getType() == PlanPatchType.ADD_NODE)
                .map(operation -> operation.getNode().getId()).toList();
        List<String> supersededResultIds = steeringPlanMode == SteeringPlanMode.LEGACY_REPLACE_PENDING
                ? results.stream().map(IntentResult::getIntentId).toList() : List.of();
        if (steeringPlanMode == SteeringPlanMode.LEGACY_REPLACE_PENDING) results.clear();
        String operationName = steeringPlanMode == SteeringPlanMode.LEGACY_REPLACE_PENDING
                ? "LEGACY_REPLACE_PENDING" : decision.action().name();
        cancelledIds.forEach(id -> publish(conversation, id, AiEventType.NODE_CANCELLED,
                "已按补充要求取消尚未执行的任务", 17));
        replacedIds.forEach(id -> publish(conversation, id, AiEventType.NODE_REPLACED,
                "已按补充要求替换尚未执行的任务", 17));
        publish(conversation, null, AiEventType.PLAN_VERSION_CREATED,
                "执行计划已更新为版本 " + plan.getVersion(), 18,
                Map.of("previousPlanVersion", previousVersion, "planVersion", plan.getVersion(),
                        "operation", operationName, "cancelledNodeIds", cancelledIds,
                        "replacedNodeIds", replacedIds, "addedNodeIds", addedIds,
                        "supersededResultIds", supersededResultIds));
        publish(conversation, null, AiEventType.STEERING_APPLIED, "已按补充要求更新后续任务", 19,
                Map.of("inputIds", inputIds(batch), "planVersion", plan.getVersion(),
                        "operation", operationName));
    }

    PlanPatch steeringPatch(SteeringPlanDecision decision, ExecutionPlan plan, int version) {
        if (decision.intents().size() > 10 || decision.targetNodeIds().size() > 20)
            throw new IllegalArgumentException("单次计划变更规模超过安全限制");
        Set<String> pendingIds = plan.pendingNodes().stream().map(PlanNode::getId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<String> targets = decision.targetNodeIds().stream().distinct().toList();
        if ((decision.action() == SteeringAction.REPLACE || decision.action() == SteeringAction.UPDATE
                || decision.action() == SteeringAction.CANCEL)
                && (targets.isEmpty() || !pendingIds.containsAll(targets))) {
            throw new IllegalArgumentException("替换或取消只能指向尚未执行的计划节点");
        }
        List<PlanPatchOperation> operations = new ArrayList<>();
        if (decision.action() == SteeringAction.ADD) {
            if (!pendingIds.containsAll(targets))
                throw new IllegalArgumentException("插入位置只能指向尚未执行的计划节点");
            List<IntentCandidate> added = rebaseIntents(decision.intents(), version);
            for (IntentCandidate intent : added) {
                PlanPatchOperation operation = new PlanPatchOperation();
                operation.setType(PlanPatchType.ADD_NODE); operation.setNode(intent);
                operation.setDependsOn(intent.getDependsOn()); operations.add(operation);
            }
            Set<String> internalDependencies = added.stream().flatMap(intent -> intent.getDependsOn().stream())
                    .collect(java.util.stream.Collectors.toSet());
            List<String> terminalIds = added.stream()
                    .filter(intent -> !internalDependencies.contains(intent.getId()))
                    .map(IntentCandidate::getId).toList();
            for (String target : targets) {
                // 独立任务不能仅凭模型建议建立前置结果绑定，否则会把新任务结果误用为旧任务答案。
                List<String> originalDependencies = plan.getNodes().get(target).getIntent().getDependsOn();
                boolean sharesInput = !originalDependencies.isEmpty() && added.stream().anyMatch(intent ->
                        intent.getDependsOn().stream().anyMatch(originalDependencies::contains));
                if (!sharesInput) continue;
                PlanPatchOperation operation = new PlanPatchOperation();
                operation.setType(PlanPatchType.UPDATE_DEPENDENCY); operation.setTargetNodeId(target);
                List<String> dependencies = new ArrayList<>(originalDependencies);
                terminalIds.stream().filter(id -> !dependencies.contains(id)).forEach(dependencies::add);
                operation.setDependsOn(dependencies); operations.add(operation);
            }
        } else if (decision.action() == SteeringAction.REPLACE || decision.action() == SteeringAction.UPDATE) {
            if (targets.size() != decision.intents().size())
                throw new IllegalArgumentException("替换节点与新意图数量必须一致");
            Map<String, String> replacementIds = new LinkedHashMap<>();
            for (int index = 0; index < targets.size(); index++)
                replacementIds.put(decision.intents().get(index).getId(), targets.get(index));
            for (int index = 0; index < targets.size(); index++) {
                IntentCandidate intent = decision.intents().get(index);
                List<String> dependencies = intent.getDependsOn().isEmpty()
                        ? plan.getNodes().get(targets.get(index)).getIntent().getDependsOn()
                        : intent.getDependsOn();
                intent.setDependsOn(dependencies.stream()
                        .map(id -> replacementIds.getOrDefault(id, id)).toList());
                PlanPatchOperation operation = new PlanPatchOperation();
                operation.setType(PlanPatchType.REPLACE_NODE); operation.setTargetNodeId(targets.get(index));
                operation.setNode(intent); operation.setDependsOn(intent.getDependsOn()); operations.add(operation);
            }
        } else if (decision.action() == SteeringAction.CANCEL) {
            Set<String> cancelled = pendingDescendants(plan, targets);
            for (String id : cancelled) {
                PlanPatchOperation operation = new PlanPatchOperation();
                operation.setType(PlanPatchType.CANCEL_NODE); operation.setTargetNodeId(id); operations.add(operation);
            }
        }
        if (operations.isEmpty()) throw new IllegalArgumentException("计划变更不能为空");
        PlanPatch patch = new PlanPatch();
        patch.setReason(displayText(decision.reason(), "用户在执行中补充了新的要求"));
        patch.setOperations(operations);
        return patch;
    }

    PlanPatch legacySteeringPatch(SteeringPlanDecision decision, ExecutionPlan plan, int version) {
        List<PlanPatchOperation> operations = new ArrayList<>();
        for (PlanNode node : plan.pendingNodes()) {
            PlanPatchOperation operation = new PlanPatchOperation();
            operation.setType(PlanPatchType.CANCEL_NODE); operation.setTargetNodeId(node.getId());
            operations.add(operation);
        }
        for (IntentCandidate intent : rebaseIntents(decision.intents(), version)) {
            PlanPatchOperation operation = new PlanPatchOperation();
            operation.setType(PlanPatchType.ADD_NODE); operation.setNode(intent);
            operation.setDependsOn(intent.getDependsOn()); operations.add(operation);
        }
        PlanPatch patch = new PlanPatch();
        patch.setReason("旧覆盖策略：取消尚未执行的意图"); patch.setOperations(operations);
        return patch;
    }

    private Set<String> pendingDescendants(ExecutionPlan plan, List<String> targets) {
        Set<String> affected = new java.util.LinkedHashSet<>(targets);
        boolean changed;
        do {
            changed = false;
            for (PlanNode node : plan.pendingNodes()) {
                if (!affected.contains(node.getId()) && node.getIntent().getDependsOn().stream().anyMatch(affected::contains))
                    changed |= affected.add(node.getId());
            }
        } while (changed);
        return affected;
    }

    private List<IntentCandidate> rebaseIntents(List<IntentCandidate> intents, int version) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (int index = 0; index < intents.size(); index++) {
            ids.put(intents.get(index).getId(), "p" + version + "-intent-" + index);
        }
        for (IntentCandidate intent : intents) {
            intent.setId(ids.get(intent.getId()));
            intent.setDependsOn(intent.getDependsOn().stream()
                    .map(id -> ids.getOrDefault(id, id)).toList());
        }
        return intents;
    }

    private void appendSteering(ConversationContext conversation, SteeringBatch batch) {
        if (chatHistoryStore == null || conversation.getConversationId() == null) return;
        for (SteeringInput input : batch.inputs()) {
            ConversationMessage value = new ConversationMessage("USER", input.content(), input.receivedAt());
            value.setExecutionId(conversation.getExecutionId());
            value.setIdempotencyKey(conversation.getExecutionId() + ":STEERING:" + input.inputId());
            chatHistoryStore.append(conversation.getConversationId(), value);
        }
    }

    private String joinInputs(String initial, SteeringBatch batch) {
        List<String> values = new ArrayList<>();
        if (initial != null && !initial.isBlank()) values.add(initial.trim());
        batch.inputs().forEach(input -> values.add(input.content()));
        return String.join("\n\n", values);
    }

    private List<String> inputIds(SteeringBatch batch) {
        return batch.inputs().stream().map(SteeringInput::inputId).toList();
    }

    private IntentResult executeIntent(IntentCandidate intent, int intentTotal,
                                       AiExecutionContext sharedContext, int completedCount) {
        AiExecutionContext context = sharedContext.forkForIntent();
        ConversationContext conversation = context.getConversation();
        log.info("AI 开始执行意图 executionId={}, intentId={}, code={}, description={}", conversation.getExecutionId(),
                intent.getId(), intent.getCode(), intent.getDescription());
        ExecutionRoute route = executionRouter.route(intent);
        publish(conversation, intent.getId(), AiEventType.PIPELINE_BRANCH, routeMessage(route), 19,
                java.util.Map.of("route", route.name(), "dependsOn", intent.getDependsOn()));
        publish(conversation, intent.getId(), AiEventType.TASK_STARTED,
                "开始任务：" + displayText(intent.getDescription(), intent.getProgressText()), 20,
                java.util.Map.of("intentIndex", completedCount + 1, "intentTotal", intentTotal,
                        "progressText", displayText(intent.getProgressText(), "正在处理"),
                        "objective", displayText(intent.getDescription(), intent.getProgressText()),
                        "expectedResult", displayText(intent.getExpectedResult(), "完成当前任务")));
        IntentResult result;
        if (hasFailedDependency(intent, context)) {
            publish(conversation, intent.getId(), AiEventType.PIPELINE_BRANCH, "前置意图失败，已跳过当前任务", 20,
                    java.util.Map.of("dependsOn", intent.getDependsOn(), "reason", "DEPENDENCY_FAILED"));
            result = IntentResult.skipped(intent, "前置意图执行失败");
        } else {
            result = intentLoopExecutor.execute(intent, context);
        }
        log.info("AI 意图执行完成 executionId={}, intentId={}, status={}", conversation.getExecutionId(), intent.getId(), result.getStatus());
        AiEventType terminalEvent = result.getStatus() == IntentStatus.SUCCESS ? AiEventType.TASK_SUCCESS
                : result.getStatus() == IntentStatus.WAITING_USER ? AiEventType.WAITING_USER : AiEventType.TASK_FAILED;
        publish(conversation, intent.getId(), terminalEvent,
                result.getMessage() == null ? "执行完成" : result.getMessage(), 80);
        return result;
    }

    private void attemptReplan(ExecutionPlan plan, PlanNode node, IntentResult result,
                               AiExecutionContext context, List<IntentResult> results) {
        TaskVerification verification = context.getVerifications().get(node.getId());
        if (verification == null || verification.getAction() != VerificationAction.REPLAN
                || plan.getReplanCount() >= Math.max(MAX_PLAN_VERSIONS - 1, 0)) return;
        publish(context.getConversation(), node.getId(), AiEventType.REPLAN_REQUESTED,
                "任务验收未通过，正在调整后续执行计划", 79,
                Map.of("planVersion", plan.getVersion(), "reason", displayText(verification.getMessage(), "验收未通过")));
        ContextRequest contextRequest = new ContextRequest();
        contextRequest.setPhase(ContextPhase.TASK_REPLANNING);
        contextRequest.setConversation(context.getConversation());
        contextRequest.setIntent(node.getIntent());
        contextRequest.setPreviousIntentResults(context.getResults());
        contextRequest.setVerification(verification);
        contextRequest.setExecutionPlan(plan);
        PlanReplanRequest request = new PlanReplanRequest();
        request.setPlan(plan);
        request.setFailedNode(node);
        request.setResult(result);
        request.setVerification(verification);
        request.setConversation(context.getConversation());
        request.setAllowedIntents(intentFactory.intentDefinitions());
        request.setContextSnapshot(contextAssembler.assemble(contextRequest));
        java.util.Optional<PlanPatch> proposed = planReplanner.replan(request);
        if (proposed.isEmpty()) return;
        PlanPatch patch = proposed.get();
        publish(context.getConversation(), node.getId(), AiEventType.PLAN_PATCH_PROPOSED,
                "已生成计划调整方案", 79, Map.of("planVersion", plan.getVersion(), "patch", patch));
        try {
            Set<String> allowedCodes = intentFactory.intentDefinitions().stream()
                    .map(IntentDefinition::getCode).collect(java.util.stream.Collectors.toSet());
            Set<String> resetIds = planPatchApplier.apply(plan, patch, allowedCodes, MAX_PLAN_VERSIONS);
            patch.getOperations().forEach(operation -> {
                if (operation.getType() == PlanPatchType.REPLACE_NODE) {
                    publish(context.getConversation(), operation.getTargetNodeId(), AiEventType.NODE_REPLACED,
                            "已替换未通过验收的计划节点", 79);
                } else if (operation.getType() == PlanPatchType.CANCEL_NODE) {
                    publish(context.getConversation(), operation.getTargetNodeId(), AiEventType.NODE_CANCELLED,
                            "已取消不再需要的计划节点", 79);
                }
            });
            resetIds.forEach(id -> {
                context.removeResult(id);
                results.removeIf(item -> id.equals(item.getIntentId()));
            });
            publish(context.getConversation(), node.getId(), AiEventType.PLAN_VERSION_CREATED,
                    "执行计划已更新为版本 " + plan.getVersion(), 79,
                    Map.of("planVersion", plan.getVersion(), "resetNodeIds", resetIds));
        } catch (IllegalArgumentException exception) {
            publish(context.getConversation(), node.getId(), AiEventType.PLAN_PATCH_REJECTED,
                    "计划调整被拒绝：" + exception.getMessage(), 79,
                    Map.of("planVersion", plan.getVersion(), "reason", exception.getMessage()));
        }
    }

    private void replaceResult(List<IntentResult> results, IntentResult value) {
        results.removeIf(item -> value.getIntentId().equals(item.getIntentId()));
        results.add(value);
    }

    private ExecutionSnapshot loadCheckpoint(ConversationContext conversation) {
        if (executionCheckpointStore == null || conversation.getConversationId() == null
                || conversation.getConversationId().isBlank()) return null;
        return executionCheckpointStore.load(conversation.getConversationId(), conversation.getExecutionId())
                .map(value -> {
                    try {
                        ExecutionSnapshot snapshot = checkpointMapper.readValue(value, ExecutionSnapshot.class);
                        if (snapshot.getPlan() == null) throw new IllegalStateException("执行检查点缺少计划");
                        return snapshot;
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalStateException("执行检查点无法解析", exception);
                    }
                }).orElse(null);
    }

    private void saveCheckpoint(ConversationContext conversation, ExecutionPlan plan,
                                List<IntentResult> results, ConversationResult finalResult) {
        if (executionCheckpointStore == null || conversation.getConversationId() == null
                || conversation.getConversationId().isBlank()) return;
        ExecutionSnapshot snapshot = new ExecutionSnapshot();
        snapshot.setPlan(plan);
        snapshot.setResults(List.copyOf(results));
        snapshot.setFinalResult(finalResult);
        try {
            executionCheckpointStore.save(conversation.getConversationId(), conversation.getExecutionId(),
                    checkpointMapper.writeValueAsString(snapshot));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("执行检查点无法序列化", exception);
        }
    }

    private PlanNodeStatus toNodeStatus(IntentResult result) {
        return switch (result.getStatus()) {
            case SUCCESS -> PlanNodeStatus.SUCCESS;
            case WAITING_USER -> PlanNodeStatus.WAITING_USER;
            case SKIPPED -> PlanNodeStatus.SKIPPED;
            default -> PlanNodeStatus.FAILED;
        };
    }

    private void appendAssistant(ConversationContext conversation, String message) {
        if (chatHistoryStore != null && conversation.getConversationId() != null) {
            ConversationMessage value = new ConversationMessage("ASSISTANT", message, System.currentTimeMillis());
            value.setExecutionId(conversation.getExecutionId());
            value.setIdempotencyKey(conversation.getExecutionId() + ":ASSISTANT");
            chatHistoryStore.append(conversation.getConversationId(), value);
        }
    }

    private void loadRequestedHistory(ConversationContext conversation, ContextRequirement requirement) {
        int limit = requirement == null ? 0 : Math.min(Math.max(requirement.getHistoryLimit(), 0), 50);
        if (requirement == null || !requirement.isRecallHistory() || limit == 0) {
            log.info("AI 历史召回跳过 executionId={}, requested={}", conversation.getExecutionId(), limit);
            return;
        }
        if (conversation.getHistory() != null && conversation.getHistory().size() >= limit) return;
        if (chatHistoryStore == null || conversation.getConversationId() == null) return;
        List<ConversationMessage> history = chatHistoryStore.loadBefore(conversation.getConversationId(), limit,
                conversation.getHistoryBefore() <= 0 ? Long.MAX_VALUE : conversation.getHistoryBefore());
        conversation.setHistory(history);
        log.info("AI 历史召回完成 executionId={}, requested={}, loaded={}", conversation.getExecutionId(), limit, history.size());
        publish(conversation, null, AiEventType.HISTORY_LOADED, "已加载 " + history.size() + " 条相关对话", 18,
                java.util.Map.of("historyCount", history.size(), "requested", limit));
    }

    /** 在全局意图识别前预加载少量历史；宿主已提供历史时不重复查询。 */
    private void preloadAnalysisHistory(ConversationContext conversation) {
        if (conversation.getHistory() != null && !conversation.getHistory().isEmpty()) return;
        if (chatHistoryStore == null || conversation.getConversationId() == null) {
            conversation.setHistory(List.of());
            return;
        }
        List<ConversationMessage> history = chatHistoryStore.loadBefore(conversation.getConversationId(),
                ANALYSIS_HISTORY_LIMIT,
                conversation.getHistoryBefore() <= 0 ? Long.MAX_VALUE : conversation.getHistoryBefore());
        conversation.setHistory(history == null ? List.of() : List.copyOf(history));
        log.info("AI 意图分析预加载历史 executionId={}, loaded={}", conversation.getExecutionId(),
                conversation.getHistory().size());
    }

    private void appendUser(ConversationContext conversation) {
        if (chatHistoryStore == null || conversation.getConversationId() == null
                || conversation.getUserInput() == null || conversation.getUserInput().isBlank()) return;
        ConversationMessage value = new ConversationMessage("USER", conversation.getUserInput(), System.currentTimeMillis());
        value.setExecutionId(conversation.getExecutionId());
        Object inputId = conversation.getAttributes() == null ? null : conversation.getAttributes().get("initialInputId");
        value.setIdempotencyKey(conversation.getExecutionId() + ":USER" +
                (inputId == null ? "" : ":" + inputId));
        value.setImageUrls(conversation.getImageUrls());
        value.setFileIds(conversation.getFileIds());
        chatHistoryStore.append(conversation.getConversationId(), value);
    }

    /** 只要任一已执行的前置意图失败，当前意图即跳过。 */
    private boolean hasFailedDependency(IntentCandidate intent, AiExecutionContext context) {
        return intent.getDependsOn().stream().map(context.getResults()::get)
                .anyMatch(result -> result != null && result.getStatus() != IntentStatus.SUCCESS);
    }

    private String intentSummary(List<IntentCandidate> intents) {
        if (intents == null || intents.isEmpty()) return "暂无可执行任务";
        return intents.stream().map(intent -> displayText(intent.getDescription(), intent.getProgressText()))
                .collect(java.util.stream.Collectors.joining(" → "));
    }

    private String displayText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? (fallback == null || fallback.isBlank() ? "处理当前请求" : fallback) : preferred;
    }

    private String routeMessage(ExecutionRoute route) {
        return switch (route) {
            case AGENT_LOOP -> "任务需要动态选择工具，将通过智能规划执行";
            case PIPELINE -> "任务依赖前置结果，将在依赖完成后继续执行";
            case DIRECT -> "任务已有明确工具，将直接执行";
        };
    }

    /** 发布与传输协议无关的标准事件；是否转为 SSE 由宿主接口层决定。 */
    private void publish(ConversationContext c, String intentId, AiEventType type, String message, int progress) {
        publish(c, intentId, type, message, progress, null);
    }

    private void publish(ConversationContext c, String intentId, AiEventType type, String message, int progress,
                         java.util.Map<String, Object> payload) {
        events.publish(new AiEvent(UUID.randomUUID().toString(), c.getConversationId(), c.getExecutionId(), intentId,
                type, message, progress, payload, null));
    }
}
