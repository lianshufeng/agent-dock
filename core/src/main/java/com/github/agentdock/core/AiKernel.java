package com.github.agentdock.core;

import com.github.agentdock.core.aggregation.impl.DefaultResultAggregator;
import com.github.agentdock.core.aggregation.CapabilityOutputCombiner;
import com.github.agentdock.core.aggregation.ResultSummarizer;
import com.github.agentdock.core.aggregation.impl.DefaultCapabilityOutputCombiner;
import com.github.agentdock.core.aggregation.impl.DefaultResultSummarizer;
import com.github.agentdock.core.capability.*;
import com.github.agentdock.core.event.*;
import com.github.agentdock.core.event.impl.InMemoryAiEventPublisher;
import com.github.agentdock.core.intent.IntentFactory;
import com.github.agentdock.core.intent.IntentRegistry;
import com.github.agentdock.core.intent.LlmIntentAnalyzer;
import com.github.agentdock.core.intent.ContextRecallPolicy;
import com.github.agentdock.core.intent.IntentAnalysisFallback;
import com.github.agentdock.core.intent.IntentAnalysisPostProcessor;
import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.ConversationResult;
import com.github.agentdock.core.model.IntentDefinition;
import com.github.agentdock.core.store.ChatHistoryStore;
import com.github.agentdock.core.store.ExecutionCheckpointStore;
import com.github.agentdock.core.loop.IntentLoopExecutor;
import com.github.agentdock.core.loop.IntentLoopPlanner;
import com.github.agentdock.core.task.TaskPlanner;
import com.github.agentdock.core.task.TaskRegistry;
import com.github.agentdock.core.task.RegistryTaskPlanner;
import com.github.agentdock.core.provider.ModelUsageRecorder;
import com.github.agentdock.core.provider.NoopModelUsageRecorder;
import com.github.agentdock.core.context.ContextAssembler;
import com.github.agentdock.core.context.DefaultContextAssembler;
import com.github.agentdock.core.task.TaskVerifier;
import com.github.agentdock.core.task.DefaultTaskVerifier;
import com.github.agentdock.core.task.TaskReplanner;
import com.github.agentdock.core.task.NoopTaskReplanner;
import com.github.agentdock.core.planning.PlanReplanner;
import com.github.agentdock.core.planning.NoopPlanReplanner;
import com.github.agentdock.core.steering.ExecutionSteering;
import com.github.agentdock.core.steering.SteeringPlanMode;
import com.github.agentdock.core.memory.*;
import com.github.agentdock.core.state.*;

/**
 * AI 内核的自管理入口。业务项目只需注册自己的适配器和能力，不需要依赖 Spring。
 */
public final class AiKernel {
    /** 意图、能力及事件订阅均由内核实例自行维护，不依赖 Spring 容器。 */
    private final IntentRegistry intentRegistry = new IntentRegistry();
    private final IntentFactory intentFactory = new IntentFactory(intentRegistry, null);
    private final CapabilityRegistry capabilityRegistry = new CapabilityRegistry();
    private final TaskRegistry taskRegistry = new TaskRegistry();
    private com.github.agentdock.core.task.TaskSelector taskSelector;

    public AiKernel registerTaskSelector(com.github.agentdock.core.task.TaskSelector selector) {
        this.taskSelector = java.util.Objects.requireNonNull(selector, "任务选择器不能为空");
        return this;
    }
    private final InMemoryAiEventPublisher eventPublisher = new InMemoryAiEventPublisher();
    private ChatHistoryStore chatHistoryStore;
    private ExecutionCheckpointStore executionCheckpointStore;
    private IntentLoopPlanner intentLoopPlanner;
    private TaskPlanner taskPlanner;
    private CapabilityVisibilityPolicy capabilityVisibilityPolicy = new AllowAllCapabilityVisibilityPolicy();
    private CapabilityResolver capabilityResolver = new DefaultCapabilityResolver(capabilityVisibilityPolicy);
    private CapabilityCandidateSelector capabilityCandidateSelector = new AllEligibleCapabilitySelector();
    private CapabilityInvoker capabilityInvoker;
    private ResultSummarizer resultSummarizer = new DefaultResultSummarizer();
    private CapabilityOutputCombiner capabilityOutputCombiner = new DefaultCapabilityOutputCombiner();
    private ModelUsageRecorder modelUsageRecorder = NoopModelUsageRecorder.INSTANCE;
    private ContextAssembler contextAssembler = new DefaultContextAssembler();
    private SessionMemoryStore sessionMemoryStore;
    private SessionMemoryRecallPolicy sessionMemoryRecallPolicy = new SessionMemoryRecallPolicy() { };
    private ConversationStateStore conversationStateStore;
    private ConversationStatePolicy conversationStatePolicy = new ConversationStatePolicy() { };
    private TaskVerifier taskVerifier = new DefaultTaskVerifier();
    private TaskReplanner taskReplanner = new NoopTaskReplanner();
    private com.github.agentdock.core.task.TaskConfirmationPolicy taskConfirmationPolicy = (task, context) -> false;
    private PlanReplanner planReplanner = new NoopPlanReplanner();
    private AiExecutionMode executionMode = AiExecutionMode.SERIAL;
    private boolean executionObservationEnabled;

    /** 默认关闭，避免改变已有宿主的事件序列。 */
    public AiKernel registerExecutionObservation(boolean enabled) {
        this.executionObservationEnabled = enabled;
        return this;
    }
    private SteeringPlanMode steeringPlanMode = "LEGACY_REPLACE_PENDING".equalsIgnoreCase(
            System.getProperty("agentdock.steering.plan-mode"))
            ? SteeringPlanMode.LEGACY_REPLACE_PENDING : SteeringPlanMode.CONSERVATIVE;

    public AiKernel registerSteeringPlanMode(SteeringPlanMode mode) {
        this.steeringPlanMode = java.util.Objects.requireNonNull(mode, "补充输入计划策略不能为空");
        return this;
    }

    public AiKernel registerExecutionMode(AiExecutionMode mode) {
        this.executionMode = mode == null ? AiExecutionMode.SERIAL : mode;
        return this;
    }

    public AiKernel registerModelUsageRecorder(ModelUsageRecorder recorder) {
        this.modelUsageRecorder = java.util.Objects.requireNonNull(recorder, "模型用量记录器不能为空");
        return this;
    }

    public AiKernel registerChatHistoryStore(ChatHistoryStore store) {
        this.chatHistoryStore = store;
        return this;
    }

    public AiKernel registerIntentLoopPlanner(IntentLoopPlanner planner) {
        this.intentLoopPlanner = planner;
        return this;
    }

    public AiKernel registerTaskPlanner(TaskPlanner planner) {
        this.taskPlanner = planner;
        return this;
    }

    /** 注册宿主任务定义；未注册任务时保持原有默认任务行为。 */
    public AiKernel registerTask(com.github.agentdock.core.model.TaskDefinition definition) {
        taskRegistry.register(definition);
        return this;
    }

    public AiKernel registerContextAssembler(ContextAssembler assembler) {
        this.contextAssembler = java.util.Objects.requireNonNull(assembler, "上下文组装器不能为空");
        refreshContextAssembler();
        return this;
    }

    /** 可选检查点；未注册时保留原有一次调用内的执行方式。 */
    public AiKernel registerExecutionCheckpointStore(ExecutionCheckpointStore store) {
        this.executionCheckpointStore = java.util.Objects.requireNonNull(store, "执行检查点存储不能为空");
        return this;
    }

    /** 会话记忆由宿主存储；未注册时不进行任何记忆查询。 */
    public AiKernel registerSessionMemoryStore(SessionMemoryStore store) {
        this.sessionMemoryStore = java.util.Objects.requireNonNull(store, "会话记忆存储不能为空");
        refreshContextAssembler();
        return this;
    }

    public AiKernel registerSessionMemoryRecallPolicy(SessionMemoryRecallPolicy policy) {
        this.sessionMemoryRecallPolicy = java.util.Objects.requireNonNull(policy, "会话记忆召回策略不能为空");
        refreshContextAssembler();
        return this;
    }

    public AiKernel registerConversationStateStore(ConversationStateStore store) {
        this.conversationStateStore = java.util.Objects.requireNonNull(store, "会话状态存储不能为空");
        refreshContextAssembler();
        return this;
    }

    public AiKernel registerConversationStatePolicy(ConversationStatePolicy policy) {
        this.conversationStatePolicy = java.util.Objects.requireNonNull(policy, "会话状态读取策略不能为空");
        refreshContextAssembler();
        return this;
    }

    private ContextAssembler effectiveContextAssembler() {
        ContextAssembler effective = sessionMemoryStore == null ? contextAssembler
                : new SessionMemoryContextAssembler(contextAssembler, sessionMemoryStore, sessionMemoryRecallPolicy);
        return conversationStateStore == null ? effective
                : new ConversationStateContextAssembler(effective, conversationStateStore, conversationStatePolicy);
    }

    private void refreshContextAssembler() {
        intentFactory.setContextAssembler(effectiveContextAssembler());
    }

    public AiKernel registerTaskVerifier(TaskVerifier verifier) {
        this.taskVerifier = java.util.Objects.requireNonNull(verifier, "任务验收器不能为空");
        return this;
    }

    /** 仅接受宿主配置的确认核验逻辑；不会读取用户属性中的 confirmed 标记。 */
    public AiKernel registerTaskConfirmationPolicy(com.github.agentdock.core.task.TaskConfirmationPolicy policy) {
        this.taskConfirmationPolicy = java.util.Objects.requireNonNull(policy, "任务确认策略不能为空");
        return this;
    }

    public AiKernel registerTaskReplanner(TaskReplanner replanner) {
        this.taskReplanner = java.util.Objects.requireNonNull(replanner, "任务重规划器不能为空");
        return this;
    }

    public AiKernel registerPlanReplanner(PlanReplanner replanner) {
        this.planReplanner = java.util.Objects.requireNonNull(replanner, "计划重规划器不能为空");
        return this;
    }

    public AiKernel registerCapabilityResolver(CapabilityResolver resolver) {
        this.capabilityResolver = java.util.Objects.requireNonNull(resolver, "能力解析器不能为空");
        return this;
    }

    /** 注册候选能力选择器；默认向 LLM 提供全部已通过硬准入的能力。 */
    public AiKernel registerCapabilityCandidateSelector(CapabilityCandidateSelector selector) {
        this.capabilityCandidateSelector = java.util.Objects.requireNonNull(selector, "候选能力选择器不能为空");
        return this;
    }

    /** 注册独立的能力可见性策略；工具契约本身不绑定任何意图编码。 */
    public AiKernel registerCapabilityVisibilityPolicy(CapabilityVisibilityPolicy policy) {
        this.capabilityVisibilityPolicy = java.util.Objects.requireNonNull(policy, "能力可见性策略不能为空");
        this.capabilityResolver = new DefaultCapabilityResolver(policy);
        return this;
    }

    public AiKernel registerCapabilityInvoker(CapabilityInvoker invoker) {
        this.capabilityInvoker = java.util.Objects.requireNonNull(invoker, "能力调用器不能为空");
        return this;
    }

    public AiKernel registerResultSummarizer(ResultSummarizer summarizer) {
        this.resultSummarizer = java.util.Objects.requireNonNull(summarizer, "结果汇总器不能为空");
        return this;
    }

    public AiKernel registerCapabilityOutputCombiner(CapabilityOutputCombiner combiner) {
        this.capabilityOutputCombiner = java.util.Objects.requireNonNull(combiner, "能力输出组合器不能为空");
        return this;
    }

    public AiKernel registerIntent(IntentDefinition definition) {
        intentRegistry.register(definition);
        return this;
    }

    /** 注册全局多意图分析器；每次会话只进行一次意图识别请求。 */
    public AiKernel registerIntentAnalyzer(LlmIntentAnalyzer analyzer) {
        intentFactory.setAnalyzer(analyzer);
        return this;
    }

    public AiKernel registerContextRecallPolicy(ContextRecallPolicy policy) {
        intentFactory.setContextRecallPolicy(policy);
        return this;
    }

    public AiKernel registerIntentAnalysisPostProcessor(IntentAnalysisPostProcessor postProcessor) {
        intentFactory.addPostProcessor(java.util.Objects.requireNonNull(postProcessor, "意图后处理器不能为空"));
        return this;
    }

    public AiKernel registerIntentAnalysisFallback(IntentAnalysisFallback fallback) {
        intentFactory.addFallback(java.util.Objects.requireNonNull(fallback, "意图分析兜底不能为空"));
        return this;
    }

    /** 注册统一能力。 */
    public AiKernel registerCapability(AiCapability capability) {
        capabilityRegistry.register(capability);
        return this;
    }

    /** 订阅执行事件，宿主可在订阅回调中转发为 SSE。 */
    public AiKernel subscribe(java.util.function.Consumer<AiEvent> subscriber) {
        eventPublisher.subscribe(subscriber);
        return this;
    }

    /** 执行一次完整会话：意图识别、排序、路由、执行和结果聚合。 */
    public ConversationResult execute(ConversationContext context) {
        return execute(context, ExecutionSteering.disabled());
    }

    /** 结构化任务入口：任务、意图和输入均在内核注册目录中校验后执行。 */
    public ConversationResult executeTask(ConversationContext context,
                                           com.github.agentdock.core.model.TaskExecutionRequest request) {
        java.util.Objects.requireNonNull(context, "会话上下文不能为空").setEventPublisher(eventPublisher);
        java.util.Objects.requireNonNull(request, "结构化任务请求不能为空");
        String intentCode = validateTaskRequest(request);
        var definition = taskRegistry.require(request.taskCode());
        var intent = new com.github.agentdock.core.model.IntentCandidate();
        intent.setId("task-" + java.util.UUID.randomUUID());
        intent.setCode(intentCode);
        intent.setDescription(definition.getDescription());
        intent.setExpectedResult(definition.getSuccessCriteria());
        intent.setConfidence(1.0);
        intent.setDependsOn(java.util.List.of());
        intent.setContextRequirement(com.github.agentdock.core.model.ContextRequirement.NONE);
        intent.setRequestedTaskCode(definition.getCode());
        intent.setTaskInputBindings(request.inputs());
        context.setModelUsageRecorder(modelUsageRecorder);
        ContextAssembler effectiveAssembler = effectiveContextAssembler();
        CapabilityInvoker invoker = capabilityInvoker == null
                ? new DefaultCapabilityInvoker(capabilityRegistry, capabilityVisibilityPolicy) : capabilityInvoker;
        return new AiExecutionEngine(intentFactory, new DefaultResultAggregator(), resultSummarizer, eventPublisher,
                new IntentLoopExecutor(intentLoopPlanner, capabilityRegistry, chatHistoryStore,
                        capabilityResolver, invoker, eventPublisher, effectiveTaskPlanner(), capabilityOutputCombiner,
                        capabilityCandidateSelector, effectiveAssembler, taskVerifier, taskReplanner)
                        .withTaskConfirmationPolicy(taskConfirmationPolicy),
                chatHistoryStore, executionMode, planReplanner, effectiveAssembler, steeringPlanMode,
                executionCheckpointStore).withExecutionObservation(executionObservationEnabled)
                .executeTask(context, intent);
    }

    /** 宿主可在创建异步执行记录前校验请求；返回解析后的意图编码。 */
    public String validateTaskRequest(com.github.agentdock.core.model.TaskExecutionRequest request) {
        java.util.Objects.requireNonNull(request, "结构化任务请求不能为空");
        taskRegistry.validateReferences(intentRegistry, capabilityRegistry);
        var definition = taskRegistry.require(request.taskCode());
        String intentCode = request.intentCode();
        if (intentCode == null || intentCode.isBlank()) {
            if (definition.getIntentCodes().size() != 1)
                throw new IllegalArgumentException("多意图任务必须显式指定 intentCode");
            intentCode = definition.getIntentCodes().iterator().next();
        }
        if (!definition.supports(intentCode)) throw new IllegalArgumentException("任务不支持指定意图: " + intentCode);
        taskRegistry.validateTaskInput(definition, request.inputs());
        return intentCode;
    }

    /** 使用可选的执行中输入通道；旧宿主继续调用单参数入口即可。 */
    public ConversationResult execute(ConversationContext context, ExecutionSteering steering) {
        java.util.Objects.requireNonNull(context, "会话上下文不能为空").setEventPublisher(eventPublisher);
        taskRegistry.validateReferences(intentRegistry, capabilityRegistry);
        context.setModelUsageRecorder(modelUsageRecorder);
        ContextAssembler effectiveAssembler = effectiveContextAssembler();
        CapabilityInvoker invoker = capabilityInvoker == null
                ? new DefaultCapabilityInvoker(capabilityRegistry, capabilityVisibilityPolicy) : capabilityInvoker;
        return new AiExecutionEngine(intentFactory, new DefaultResultAggregator(), resultSummarizer, eventPublisher,
                new IntentLoopExecutor(intentLoopPlanner, capabilityRegistry, chatHistoryStore,
                        capabilityResolver, invoker, eventPublisher,
                        effectiveTaskPlanner(), capabilityOutputCombiner,
                        capabilityCandidateSelector, effectiveAssembler, taskVerifier, taskReplanner)
                        .withTaskConfirmationPolicy(taskConfirmationPolicy),
                chatHistoryStore, executionMode, planReplanner, effectiveAssembler, steeringPlanMode,
                executionCheckpointStore)
                .withExecutionObservation(executionObservationEnabled)
                .execute(context, steering == null ? ExecutionSteering.disabled() : steering);
    }

    private TaskPlanner effectiveTaskPlanner() {
        return taskRegistry.isEmpty() ? taskPlanner : new RegistryTaskPlanner(taskRegistry, taskPlanner, taskSelector);
    }

    public void shutdownExecutors() {
        IntentLoopExecutor.shutdownExecutor();
        DefaultCapabilityInvoker.shutdownExecutor();
    }
}
