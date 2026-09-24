package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.IntentComplexity;
import com.github.agentdock.core.context.*;
import com.github.agentdock.core.steering.*;
import java.util.*;

/** 多意图工厂：收集适配器结果，合并上下文要求并生成串行队列顺序。 */
public class IntentFactory {
    /** 已确认依赖历史时的通用最小窗口，避免模型无法感知窗口外信息却低估召回数量。 */
    private static final int MIN_RECALLED_HISTORY_LIMIT = 20;
    /** 兼容旧宿主注册入口；通用内核不再依赖业务意图适配器。 */
    private final IntentRegistry intentRegistry;
    private LlmIntentAnalyzer analyzer;
    private ContextAssembler contextAssembler = new DefaultContextAssembler();
    private ContextRecallPolicy contextRecallPolicy = ContextRecallPolicy.noop();
    private final List<IntentAnalysisPostProcessor> postProcessors = new ArrayList<>();
    private final List<IntentAnalysisFallback> fallbacks = new ArrayList<>();

    public IntentFactory() {
        this(new IntentRegistry(), null);
    }

    public IntentFactory(LlmIntentAnalyzer analyzer) {
        this(new IntentRegistry(), analyzer);
    }

    public IntentFactory(IntentRegistry intentRegistry, LlmIntentAnalyzer analyzer) {
        this.intentRegistry = Objects.requireNonNull(intentRegistry, "意图注册表不能为空");
        this.analyzer = analyzer;
    }

    /** 设置全局意图分析器；一次用户输入只调用一次。 */
    public void setAnalyzer(LlmIntentAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public void setContextAssembler(ContextAssembler contextAssembler) {
        this.contextAssembler = contextAssembler == null ? new DefaultContextAssembler() : contextAssembler;
    }

    public void setContextRecallPolicy(ContextRecallPolicy contextRecallPolicy) {
        this.contextRecallPolicy = contextRecallPolicy == null ? ContextRecallPolicy.noop() : contextRecallPolicy;
    }

    public void addPostProcessor(IntentAnalysisPostProcessor postProcessor) {
        if (postProcessor != null) postProcessors.add(postProcessor);
    }

    public void addFallback(IntentAnalysisFallback fallback) {
        if (fallback != null) fallbacks.add(fallback);
    }

    public List<IntentDefinition> intentDefinitions() {
        return intentRegistry.definitions();
    }

    /** 汇总全部通用意图，一次调用 LLM，再生成按依赖关系和优先级排序的串行计划。 */
    public IntentAnalysis analyze(ConversationContext context) {
        return analyze(context, null, Map.of());
    }

    /** 在执行中重新识别意图时，额外向上下文工程提供当前计划和已完成结果。 */
    public IntentAnalysis analyze(ConversationContext context, Object executionPlan,
                                  Map<String, IntentResult> previousIntentResults) {
        String intentCatalog = buildIntentCatalog();
        IntentAdapterResult analysis;
        RuntimeException analysisFailure = null;
        try {
            ContextRequest contextRequest = new ContextRequest();
            contextRequest.setPhase(ContextPhase.INTENT_ANALYSIS);
            contextRequest.setConversation(context);
            contextRequest.setExecutionPlan(executionPlan);
            contextRequest.setPreviousIntentResults(previousIntentResults == null ? Map.of() : previousIntentResults);
            ContextSnapshot snapshot = contextAssembler.assemble(contextRequest);
            analysis = Optional.ofNullable(analyzer == null ? null : analyzer.analyze(context, intentCatalog, snapshot))
                    .orElse(IntentAdapterResult.empty());
        } catch (RuntimeException exception) {
            analysisFailure = exception;
            analysis = IntentAdapterResult.empty();
        }
        if (isEmpty(analysis)) analysis = applyFallbacks(context, intentCatalog);
        if (isEmpty(analysis) && analysisFailure != null) throw analysisFailure;
        return normalize(context, analysis, Set.of());
    }

    /** 补充输入仅识别新增意图，并由模型显式声明对旧计划的影响范围。 */
    public SteeringPlanDecision analyzeSteering(ConversationContext context, Object executionPlan,
                                                Map<String, IntentResult> previousIntentResults) {
        String intentCatalog = buildIntentCatalog();
        ContextRequest request = new ContextRequest();
        request.setPhase(ContextPhase.INTENT_ANALYSIS);
        request.setConversation(context);
        request.setExecutionPlan(executionPlan);
        request.setPreviousIntentResults(previousIntentResults == null ? Map.of() : previousIntentResults);
        ContextSnapshot snapshot = contextAssembler.assemble(request);
        SteeringAdapterResult proposed = analyzer == null ? null
                : analyzer.analyzeSteering(context, intentCatalog, snapshot);
        if (proposed == null) proposed = new SteeringAdapterResult();
        IntentAdapterResult candidates = new IntentAdapterResult();
        candidates.setCandidates(proposed.getCandidates());
        candidates.setClarificationQuestion(proposed.getClarificationQuestion());
        Set<String> existingIds = executionPlan instanceof com.github.agentdock.core.planning.ExecutionPlan plan
                ? Set.copyOf(plan.getNodes().keySet()) : Set.of();
        IntentAnalysis normalized = normalize(context, candidates, existingIds);
        return new SteeringPlanDecision(proposed.getAction(), proposed.getTargetNodeIds(),
                normalized.getOrderedIntents(), proposed.getReason(), normalized.getClarificationQuestion());
    }

    private IntentAnalysis normalize(ConversationContext context, IntentAdapterResult analysis,
                                     Set<String> externalDependencyIds) {
        for (IntentAnalysisPostProcessor postProcessor : postProcessors) {
            analysis = Optional.ofNullable(postProcessor.process(context, analysis))
                    .orElseThrow(() -> new IllegalStateException("意图分析后处理器未返回结果"));
        }
        List<IntentCandidate> candidates = new ArrayList<>();
        ContextRequirement requirement = analysis.getContextRequirement();
        int order = 0;
        for (IntentCandidate candidate : Optional.ofNullable(analysis.getCandidates()).orElse(List.of())) {
            if (candidate.getConfidence() <= 0) continue;
            IntentCandidate normalized = candidate;
            if (normalized.getId() == null || normalized.getId().isBlank()) {
                normalized.setId("intent-" + order);
            }
            if (normalized.getComplexity() == null) normalized.setComplexity(IntentComplexity.SIMPLE);
            if ((normalized.getCode() == null || normalized.getCode().isBlank()) && normalized.getIntentType() != null) {
                normalized.setCode(normalized.getIntentType());
            }
            IntentDefinition definition = intentRegistry.find(normalized.getCode());
            if (definition == null) throw new IllegalArgumentException("模型返回了未注册意图: " + normalized.getCode());
            normalized.setIntentType(normalized.getCode());
            if (normalized.getDependsOn() == null) normalized.setDependsOn(List.of());
            if (normalized.getContextRequirement() == null) {
                normalized.setContextRequirement(ContextRequirement.NONE);
            }
            normalized.setContextRequirement(normalizeContextRequirement(normalized.getContextRequirement()));
            // 意图候选不携带工具编码，统一交给 Loop 搜索和选择能力。
            normalized.setOriginalOrder(order++);
            if (normalized.getDescription() == null || normalized.getDescription().isBlank()) {
                normalized.setDescription(definition.getDescription());
            }
            if (normalized.getProgressText() == null || normalized.getProgressText().isBlank()) {
                normalized.setProgressText("开始" + normalized.getDescription());
            }
            if (normalized.getExpectedResult() == null || normalized.getExpectedResult().isBlank()) {
                throw new IllegalArgumentException("意图缺少预期结果: " + normalized.getCode());
            }
            candidates.add(normalized);
        }
        contextRecallPolicy.apply(context, candidates);
        requirement = normalizeContextRequirement(Optional.ofNullable(requirement).orElse(ContextRequirement.NONE));
        for (IntentCandidate candidate : candidates) {
            candidate.setContextRequirement(normalizeContextRequirement(candidate.getContextRequirement()));
            requirement = requirement.merge(candidate.getContextRequirement());
        }
        validateDependencies(candidates, externalDependencyIds);
        List<IntentCandidate> ordered = topologicalSort(candidates);
        List<DeferredBranch> branches = Optional.ofNullable(analysis.getDeferredBranches()).orElse(List.of());
        Set<String> candidateIds = ordered.stream().map(IntentCandidate::getId).collect(java.util.stream.Collectors.toSet());
        Set<String> branchIds = new HashSet<>();
        for (DeferredBranch branch : branches) {
            if (branch == null || branch.getId() == null || !branchIds.add(branch.getId())
                    || !candidateIds.contains(branch.getTriggerIntentId())
                    || branch.getChoices() == null || branch.getChoices().isEmpty())
                throw new IllegalArgumentException("条件分支无效");
            Set<String> choiceIds = new HashSet<>();
            for (DeferredBranch.Choice choice : branch.getChoices()) {
                if (choice == null || choice.getId() == null || !choiceIds.add(choice.getId())
                        || choice.getCondition() == null || choice.getCondition().isBlank()
                        || choice.getGoal() == null || choice.getGoal().isBlank())
                    throw new IllegalArgumentException("条件分支选择无效");
            }
        }
        IntentAnalysis result = new IntentAnalysis(ordered, requirement, analysis.getClarificationQuestion());
        result.setDeferredBranches(List.copyOf(branches));
        return result;
    }

    public DeferredBranchDecision resolveDeferredBranch(ConversationContext context,
            com.github.agentdock.core.planning.ExecutionPlan plan, DeferredBranch branch, IntentResult triggerResult) {
        ContextRequest request = new ContextRequest();
        request.setPhase(ContextPhase.INTENT_ANALYSIS);
        request.setConversation(context);
        request.setExecutionPlan(plan);
        request.setPreviousIntentResults(Map.of(branch.getTriggerIntentId(), triggerResult));
        DeferredBranchDecision decision = analyzer == null ? null : analyzer.resolveDeferredBranch(context,
                buildIntentCatalog(), contextAssembler.assemble(request), branch, triggerResult);
        if (decision != null && "NO_MATCH".equals(decision.getOutcome())) {
            if (decision.getSelectedChoiceId() != null && !decision.getSelectedChoiceId().isBlank())
                throw new IllegalArgumentException("未命中分支不能同时选择目标");
            return decision;
        }
        if (decision == null || decision.getSelectedChoiceId() == null || decision.getSelectedChoiceId().isBlank())
            return new DeferredBranchDecision();
        if (branch.getChoices().stream().noneMatch(choice -> choice.getId().equals(decision.getSelectedChoiceId())))
            throw new IllegalArgumentException("模型选择了未登记的条件分支");
        IntentAdapterResult selected = new IntentAdapterResult(decision.getCandidates(), ContextRequirement.NONE, null);
        List<IntentCandidate> intents = normalize(context, selected, Set.of(branch.getTriggerIntentId())).getOrderedIntents();
        if (intents.isEmpty()) throw new IllegalArgumentException("选中分支没有可执行意图");
        for (IntentCandidate intent : intents) {
            if (intent.getDependsOn().isEmpty()) intent.setDependsOn(List.of(branch.getTriggerIntentId()));
        }
        decision.setCandidates(intents);
        return decision;
    }

    private IntentAdapterResult applyFallbacks(ConversationContext context, String intentCatalog) {
        for (IntentAnalysisFallback fallback : fallbacks) {
            IntentAdapterResult result = fallback.fallback(context, intentCatalog);
            if (!isEmpty(result)) return result;
        }
        return IntentAdapterResult.empty();
    }

    private boolean isEmpty(IntentAdapterResult result) {
        return result == null || result.getCandidates() == null || result.getCandidates().isEmpty();
    }

    private ContextRequirement normalizeContextRequirement(ContextRequirement requirement) {
        int requested = Math.max(requirement.getHistoryLimit(), 0);
        int limit = Math.min(requirement.isRecallHistory()
                ? Math.max(requested, MIN_RECALLED_HISTORY_LIMIT) : requested, 50);
        return new ContextRequirement(requirement.isRecallHistory() && limit > 0, limit,
                requirement.getScopes() == null ? Set.of() : requirement.getScopes());
    }

    private void validateDependencies(List<IntentCandidate> candidates, Set<String> externalDependencyIds) {
        Set<String> ids = new HashSet<>();
        for (IntentCandidate candidate : candidates) {
            if (!ids.add(candidate.getId())) throw new IllegalArgumentException("意图实例 ID 重复: " + candidate.getId());
        }
        for (IntentCandidate candidate : candidates) {
            for (String dependency : candidate.getDependsOn()) {
                if (!ids.contains(dependency) && !externalDependencyIds.contains(dependency)) {
                    throw new IllegalArgumentException("意图依赖不存在: " + dependency);
                }
                if (candidate.getId().equals(dependency)) {
                    throw new IllegalArgumentException("意图不能依赖自身: " + candidate.getId());
                }
            }
        }
    }

    /** 从宿主注册意图生成目录；不读取能力目录。 */
    private String buildIntentCatalog() {
        StringJoiner catalog = new StringJoiner("\n");
        for (IntentDefinition definition : intentRegistry.definitions()) {
            catalog.add("- " + definition.getCode() + ": " + definition.getDescription());
        }
        if (catalog.length() == 0) throw new IllegalStateException("宿主未注册任何意图定义");
        return catalog.toString();
    }

    /**
     * 对意图依赖做拓扑排序；当前结果仍是串行队列，不在此处执行并发调度。
     */
    private List<IntentCandidate> topologicalSort(List<IntentCandidate> candidates) {
        Map<String, IntentCandidate> byId = new LinkedHashMap<>();
        candidates.forEach(candidate -> byId.putIfAbsent(candidate.getId(), candidate));
        List<IntentCandidate> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (IntentCandidate candidate : candidates.stream().sorted(Comparator.comparingInt(IntentCandidate::getPriority).reversed()
                .thenComparingInt(IntentCandidate::getOriginalOrder)).toList()) dfs(candidate, byId, visited, visiting, result);
        return result;
    }

    /** 深度遍历依赖，并在生成执行队列时检测循环依赖。 */
    private void dfs(IntentCandidate candidate, Map<String, IntentCandidate> byId, Set<String> visited,
                      Set<String> visiting, List<IntentCandidate> result) {
        if (visited.contains(candidate.getId())) return;
        if (!visiting.add(candidate.getId())) throw new IllegalArgumentException("意图依赖存在环: " + candidate.getId());
        candidate.getDependsOn().stream().map(byId::get).filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(IntentCandidate::getPriority).reversed()).forEach(dep -> dfs(dep, byId, visited, visiting, result));
        visiting.remove(candidate.getId());
        visited.add(candidate.getId());
        result.add(candidate);
    }
}
