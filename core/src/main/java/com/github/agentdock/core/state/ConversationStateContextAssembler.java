package com.github.agentdock.core.state;

import com.github.agentdock.core.context.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 在模型分类及任务规划前读取宿主指定的最新会话状态。 */
public final class ConversationStateContextAssembler implements ContextAssembler {
    private final ContextAssembler delegate;
    private final ConversationStateStore store;
    private final ConversationStatePolicy policy;

    public ConversationStateContextAssembler(ContextAssembler delegate, ConversationStateStore store,
                                             ConversationStatePolicy policy) {
        this.delegate = Objects.requireNonNull(delegate);
        this.store = Objects.requireNonNull(store);
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public ContextSnapshot assemble(ContextRequest request) {
        if (request == null || request.getConversation() == null) return delegate.assemble(request);
        String conversationId = request.getConversation().getConversationId();
        if (conversationId == null || conversationId.isBlank()) return delegate.assemble(request);
        List<String> keys = request.getPhase() == ContextPhase.INTENT_ANALYSIS
                ? policy.beforeAnalysis(request.getConversation())
                : request.getIntent() == null ? List.of() : policy.forIntent(request.getConversation(), request.getIntent());
        List<ConversationState> states = new ArrayList<>();
        if (keys != null) for (String key : keys) {
            if (key != null && !key.isBlank()) store.get(conversationId, key).ifPresent(states::add);
        }
        request.setConversationStates(List.copyOf(states));
        return delegate.assemble(request);
    }
}
