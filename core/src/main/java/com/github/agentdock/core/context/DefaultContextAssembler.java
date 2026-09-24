package com.github.agentdock.core.context;

import com.github.agentdock.core.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 默认确定性上下文工程：标记来源、按优先级裁剪，并保证当前输入和直接证据优先保留。 */
public final class DefaultContextAssembler implements ContextAssembler {
    private final ContextPolicy policy;

    public DefaultContextAssembler() {
        this(ContextPolicy.defaults());
    }

    public DefaultContextAssembler(ContextPolicy policy) {
        this.policy = policy == null ? ContextPolicy.defaults() : policy;
    }

    @Override
    public ContextSnapshot assemble(ContextRequest request) {
        if (request == null) return ContextSnapshot.EMPTY;
        ContextBudget budget = policy.budget(request);
        if (budget == null) budget = ContextBudget.defaults(request.getPhase());
        List<ContextItem> candidates = collect(request, budget.maxItemCharacters());
        candidates.sort(Comparator.comparingInt(ContextItem::getPriority).reversed()
                .thenComparing(Comparator.comparingLong(ContextItem::getCreatedAt).reversed()));
        List<ContextItem> selected = new ArrayList<>();
        int tokens = 0;
        for (ContextItem item : candidates) {
            if (selected.size() >= budget.maxItems()) break;
            int next = tokens + item.getEstimatedTokens();
            if (!selected.isEmpty() && next > budget.maxTokens()) continue;
            selected.add(item);
            tokens = next;
        }
        return new ContextSnapshot(request.getPhase(), selected, tokens,
                Math.max(candidates.size() - selected.size(), 0));
    }

    private List<ContextItem> collect(ContextRequest request, int maxCharacters) {
        List<ContextItem> items = new ArrayList<>();
        ConversationContext conversation = request.getConversation();
        if (conversation != null) {
            add(items, "user-input", ContextSourceType.USER_INPUT, null, conversation.getUserInput(),
                    100, ContextTrustLevel.USER_PROVIDED, false, maxCharacters, System.currentTimeMillis());
            if (conversation.getAttributes() != null)
                add(items, "request-time-anchor", ContextSourceType.HOST_ATTRIBUTE, null,
                        conversation.getAttributes().get("requestTimeAnchor"),
                        99, ContextTrustLevel.HOST_VERIFIED, false, maxCharacters, System.currentTimeMillis());
            if (conversation.getAttributes() != null && !conversation.getAttributes().isEmpty()) {
                add(items, "host-attributes", ContextSourceType.HOST_ATTRIBUTE, null, conversation.getAttributes(),
                        85, ContextTrustLevel.HOST_VERIFIED, true, maxCharacters, System.currentTimeMillis());
            }
            List<ConversationMessage> history = Objects.requireNonNullElse(conversation.getHistory(), List.of());
            for (int index = 0; index < history.size(); index++) {
                ConversationMessage message = history.get(index);
                add(items, "history-" + index, ContextSourceType.CONVERSATION_HISTORY,
                        message.getExecutionId(), Map.of("role", Objects.toString(message.getRole(), ""),
                                "content", Objects.toString(message.getContent(), "")),
                        45, ContextTrustLevel.USER_PROVIDED, false, maxCharacters, message.getTimestamp());
            }
            List<AttachmentContent> attachments = Objects.requireNonNullElse(conversation.getAttachmentContents(), List.of());
            for (int index = 0; index < attachments.size(); index++) {
                AttachmentContent attachment = attachments.get(index);
                add(items, "attachment-" + index, ContextSourceType.ATTACHMENT, attachment.fileId(),
                        Map.of("fileName", Objects.toString(attachment.fileName(), ""),
                                "contentType", Objects.toString(attachment.contentType(), ""),
                                "content", Objects.toString(attachment.content(), "")),
                        60, ContextTrustLevel.EXTERNAL_UNTRUSTED, false, maxCharacters, System.currentTimeMillis());
            }
        }
        if (request.getExecutionPlan() != null) {
            add(items, "execution-plan", ContextSourceType.EXECUTION_PLAN, null, request.getExecutionPlan(),
                    95, ContextTrustLevel.SYSTEM, false, maxCharacters, System.currentTimeMillis());
        }
        if (request.getSessionMemories() != null) request.getSessionMemories().forEach(memory ->
                add(items, "session-memory-" + memory.key(), ContextSourceType.SESSION_MEMORY,
                        memory.key(), memory.value(), 96, ContextTrustLevel.USER_PROVIDED,
                        false, maxCharacters, memory.updatedAt()));
        if (request.getConversationStates() != null) request.getConversationStates().forEach(state ->
                add(items, "session-state-" + state.key(), ContextSourceType.SESSION_STATE,
                        state.key(), state.value(), 97, ContextTrustLevel.HOST_VERIFIED,
                        false, maxCharacters, state.updatedAt()));
        if (request.getPreviousIntentResults() != null) request.getPreviousIntentResults().forEach((id, result) ->
                add(items, "dependency-" + id, ContextSourceType.DEPENDENCY_RESULT, id, result,
                        90, ContextTrustLevel.TOOL_VERIFIED, false, maxCharacters, System.currentTimeMillis()));
        List<AgentObservation> observations = Objects.requireNonNullElse(request.getObservations(), List.of());
        for (int index = 0; index < observations.size(); index++) {
            AgentObservation observation = observations.get(index);
            ContextSourceType sourceType = observation.getResult() != null && !observation.getResult().isSuccess()
                    ? ContextSourceType.FAILURE_RECORD : ContextSourceType.TOOL_OBSERVATION;
            add(items, "observation-" + index, sourceType, observation.getToolCode(), observation,
                    sourceType == ContextSourceType.FAILURE_RECORD ? 92 : 80,
                    ContextTrustLevel.TOOL_VERIFIED, false, maxCharacters, System.currentTimeMillis());
        }
        if (request.getVerification() != null) {
            add(items, "verification", ContextSourceType.VERIFICATION_EVIDENCE, null, request.getVerification(),
                    94, ContextTrustLevel.HOST_VERIFIED, false, maxCharacters, System.currentTimeMillis());
        }
        return items;
    }

    private void add(List<ContextItem> items, String id, ContextSourceType sourceType, String sourceId,
                     Object content, int priority, ContextTrustLevel trustLevel, boolean sensitive,
                     int maxCharacters, long createdAt) {
        if (content == null || content instanceof String value && value.isBlank()) return;
        Object normalized = truncate(content, maxCharacters);
        int estimatedTokens = Math.max(1, Objects.toString(normalized, "").length() / 4);
        items.add(new ContextItem(id, sourceType, sourceId, normalized, priority, trustLevel,
                estimatedTokens, sensitive, createdAt));
    }

    private Object truncate(Object value, int maxCharacters) {
        String text = Objects.toString(value, "");
        if (text.length() <= maxCharacters) return value;
        return text.substring(0, Math.max(0, maxCharacters - 12)) + "…（已裁剪）";
    }
}
