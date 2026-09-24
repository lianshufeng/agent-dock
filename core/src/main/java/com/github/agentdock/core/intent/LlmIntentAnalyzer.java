package com.github.agentdock.core.intent;

import com.github.agentdock.core.context.ContextSnapshot;
import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentAdapterResult;
import com.github.agentdock.core.model.DeferredBranch;
import com.github.agentdock.core.model.DeferredBranchDecision;
import com.github.agentdock.core.model.IntentResult;
import com.github.agentdock.core.steering.SteeringAdapterResult;

/** LLM 供应商适配接口；AI 内核不绑定具体模型 SDK。 */
public interface LlmIntentAnalyzer {
    /** 一次接收全部通用意图定义，返回完整的多意图结构化结果。 */
    IntentAdapterResult analyze(ConversationContext context, String intentCatalog);

    /** 新版入口接收受预算控制的上下文快照；旧适配器继续通过默认方法工作。 */
    default IntentAdapterResult analyze(ConversationContext context, String intentCatalog,
                                        ContextSnapshot snapshot) {
        return analyze(context, intentCatalog);
    }

    /**
     * 判断补充输入应如何影响当前计划。旧适配器无需修改，默认将新输入作为追加意图处理。
     */
    default SteeringAdapterResult analyzeSteering(ConversationContext context, String intentCatalog,
                                                   ContextSnapshot snapshot) {
        IntentAdapterResult analysis = analyze(context, intentCatalog, snapshot);
        SteeringAdapterResult result = new SteeringAdapterResult();
        result.setCandidates(analysis == null ? java.util.List.of() : analysis.getCandidates());
        result.setClarificationQuestion(analysis == null ? null : analysis.getClarificationQuestion());
        return result;
    }

    /** 前置结果到达后选择一个待决目标；旧适配器保持安全的未决状态。 */
    default DeferredBranchDecision resolveDeferredBranch(ConversationContext context, String intentCatalog,
            ContextSnapshot snapshot, DeferredBranch branch, IntentResult triggerResult) {
        return new DeferredBranchDecision();
    }
}
