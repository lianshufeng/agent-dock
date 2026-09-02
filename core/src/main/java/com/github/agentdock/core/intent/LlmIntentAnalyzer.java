package com.github.agentdock.core.intent;

import com.github.agentdock.core.context.ContextSnapshot;
import com.github.agentdock.core.model.ConversationContext;
import com.github.agentdock.core.model.IntentAdapterResult;

/** LLM 供应商适配接口；AI 内核不绑定具体模型 SDK。 */
public interface LlmIntentAnalyzer {
    /** 一次接收全部通用意图定义，返回完整的多意图结构化结果。 */
    IntentAdapterResult analyze(ConversationContext context, String intentCatalog);

    /** 新版入口接收受预算控制的上下文快照；旧适配器继续通过默认方法工作。 */
    default IntentAdapterResult analyze(ConversationContext context, String intentCatalog,
                                        ContextSnapshot snapshot) {
        return analyze(context, intentCatalog);
    }
}
