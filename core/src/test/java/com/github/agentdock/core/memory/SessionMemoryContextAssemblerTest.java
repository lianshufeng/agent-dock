package com.github.agentdock.core.memory;

import com.github.agentdock.core.context.*;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.state.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryContextAssemblerTest {
    @Test
    void classificationSeesOnlyRequestedMemoryFromCurrentSession() {
        FakeStore store = new FakeStore();
        store.upsert("world-1", new SessionMemory("controlMode", "drone", 1));
        store.upsert("world-1", new SessionMemory("privateNote", "do not load", 1));
        store.upsert("world-2", new SessionMemory("controlMode", "robot", 1));
        SessionMemoryRecallPolicy policy = new SessionMemoryRecallPolicy() {
            @Override public List<SessionMemoryQuery> beforeAnalysis(ConversationContext conversation) {
                return List.of(SessionMemoryQuery.key("controlMode"));
            }
        };
        var assembler = new SessionMemoryContextAssembler(new DefaultContextAssembler(), store, policy);

        assertEquals("drone", memoryValue(assembler, "world-1"));
        assertEquals("robot", memoryValue(assembler, "world-2"));
        store.delete("world-1", "controlMode");
        assertNull(memoryValue(assembler, "world-1"));
        assertEquals(3, store.reads);
    }

    @Test
    void withoutPolicyNoMemoryIsRead() {
        FakeStore store = new FakeStore();
        var assembler = new SessionMemoryContextAssembler(new DefaultContextAssembler(), store,
                new SessionMemoryRecallPolicy() { });
        assertNull(memoryValue(assembler, "world-1"));
        assertEquals(0, store.reads);
    }

    @Test
    void updatedSessionStateIsReadBeforeEachClassification() {
        Map<String, ConversationState> states = new HashMap<>();
        ConversationStateStore store = new ConversationStateStore() {
            public Optional<ConversationState> get(String sessionId, String key) {
                return Optional.ofNullable(states.get(sessionId + ":" + key));
            }
            public void put(String sessionId, ConversationState state) {
                states.put(sessionId + ":" + state.key(), state);
            }
            public void delete(String sessionId, String key) { states.remove(sessionId + ":" + key); }
        };
        ConversationStatePolicy policy = new ConversationStatePolicy() {
            @Override public List<String> beforeAnalysis(ConversationContext conversation) {
                return List.of("mode");
            }
        };
        var assembler = new ConversationStateContextAssembler(new DefaultContextAssembler(), store, policy);
        store.put("world-1", new ConversationState("mode", "drone", 1));
        assertEquals("drone", stateValue(assembler, "world-1"));
        assertNull(stateValue(assembler, "world-2"));
        store.delete("world-1", "mode");
        assertNull(stateValue(assembler, "world-1"));
    }

    private Object stateValue(ContextAssembler assembler, String sessionId) {
        ContextRequest request = new ContextRequest();
        request.setPhase(ContextPhase.INTENT_ANALYSIS);
        request.setConversation(new ConversationContext(sessionId, "run", "user", "起飞", List.of(), Map.of()));
        return assembler.assemble(request).getItems().stream()
                .filter(item -> item.getSourceType() == ContextSourceType.SESSION_STATE)
                .map(ContextItem::getContent).findFirst().orElse(null);
    }

    private Object memoryValue(ContextAssembler assembler, String sessionId) {
        ContextRequest request = new ContextRequest();
        request.setPhase(ContextPhase.INTENT_ANALYSIS);
        request.setConversation(new ConversationContext(sessionId, "run", "user", "起飞", List.of(), Map.of()));
        return assembler.assemble(request).getItems().stream()
                .filter(item -> item.getSourceType() == ContextSourceType.SESSION_MEMORY)
                .map(ContextItem::getContent).findFirst().orElse(null);
    }

    private static final class FakeStore implements SessionMemoryStore {
        final Map<String, Map<String, SessionMemory>> data = new HashMap<>();
        int reads;
        public List<SessionMemory> recall(String conversationId, SessionMemoryQuery query) {
            reads++;
            return Optional.ofNullable(data.getOrDefault(conversationId, Map.of()).get(query.key())).stream().toList();
        }
        public void upsert(String conversationId, SessionMemory memory) {
            data.computeIfAbsent(conversationId, ignored -> new HashMap<>()).put(memory.key(), memory);
        }
        public void delete(String conversationId, String key) { data.getOrDefault(conversationId, Map.of()).remove(key); }
    }
}
