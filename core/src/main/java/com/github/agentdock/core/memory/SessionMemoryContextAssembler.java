package com.github.agentdock.core.memory;

import com.github.agentdock.core.context.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 在各阶段按宿主策略读取会话记忆，再交给原有上下文组装器裁剪。 */
public final class SessionMemoryContextAssembler implements ContextAssembler {
    private final ContextAssembler delegate;
    private final SessionMemoryStore store;
    private final SessionMemoryRecallPolicy policy;

    public SessionMemoryContextAssembler(ContextAssembler delegate, SessionMemoryStore store,
                                         SessionMemoryRecallPolicy policy) {
        this.delegate = Objects.requireNonNull(delegate);
        this.store = Objects.requireNonNull(store);
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public ContextSnapshot assemble(ContextRequest request) {
        if (request == null || request.getConversation() == null) return delegate.assemble(request);
        String conversationId = request.getConversation().getConversationId();
        if (conversationId == null || conversationId.isBlank()) return delegate.assemble(request);
        List<SessionMemoryQuery> queries = request.getPhase() == ContextPhase.INTENT_ANALYSIS
                ? policy.beforeAnalysis(request.getConversation())
                : request.getIntent() == null ? List.of() : policy.forIntent(request.getConversation(), request.getIntent());
        List<SessionMemory> memories = new ArrayList<>();
        if (queries != null) for (SessionMemoryQuery query : queries) {
            if (query == null) continue;
            List<SessionMemory> recalled = store.recall(conversationId, query);
            if (recalled != null) memories.addAll(recalled.stream().filter(Objects::nonNull)
                    .limit(query.limit()).toList());
        }
        request.setSessionMemories(List.copyOf(memories));
        return delegate.assemble(request);
    }
}
