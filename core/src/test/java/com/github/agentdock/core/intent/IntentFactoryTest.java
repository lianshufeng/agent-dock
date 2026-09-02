package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.IntentComplexity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntentFactoryTest {
    @Test
    void usesFallbackAfterAnalyzerFailureAndThenRunsPostProcessor() {
        IntentRegistry registry = new IntentRegistry();
        registry.register(new IntentDefinition("LOOKUP", "查找信息"));
        IntentFactory factory = new IntentFactory(registry, (context, catalog) -> {
            throw new IllegalStateException("model unavailable");
        });
        factory.addFallback((context, catalog) -> result(candidate("fallback", "LOOKUP")));
        factory.addPostProcessor((context, analysis) -> {
            analysis.getCandidates().get(0).setDescription("processed");
            return analysis;
        });

        IntentAnalysis analysis = factory.analyze(conversation());

        assertEquals(1, analysis.getOrderedIntents().size());
        assertEquals("LOOKUP", analysis.getOrderedIntents().get(0).getCode());
        assertEquals("processed", analysis.getOrderedIntents().get(0).getDescription());
    }

    @Test
    void rejectsCircularDependenciesAfterPostProcessing() {
        IntentRegistry registry = new IntentRegistry();
        registry.register(new IntentDefinition("LOOKUP", "查找信息"));
        IntentFactory factory = new IntentFactory(registry, (context, catalog) -> result(candidate("one", "LOOKUP")));
        factory.addPostProcessor((context, analysis) -> {
            IntentCandidate one = analysis.getCandidates().get(0);
            IntentCandidate two = candidate("two", "LOOKUP");
            one.setDependsOn(List.of("two"));
            two.setDependsOn(List.of("one"));
            analysis.setCandidates(List.of(one, two));
            return analysis;
        });

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> factory.analyze(conversation()));
        assertTrue(error.getMessage().contains("依赖存在环"));
    }

    private IntentCandidate candidate(String id, String code) {
        return new IntentCandidate(id, code, "lookup", "result", 0.9, 1, 0,
                List.of(), IntentComplexity.SIMPLE, ContextRequirement.NONE, null);
    }

    private IntentAdapterResult result(IntentCandidate candidate) {
        return new IntentAdapterResult(List.of(candidate), ContextRequirement.NONE, null);
    }

    private ConversationContext conversation() {
        return new ConversationContext("conversation", "execution", "user", "input", List.of(), Map.of());
    }
}
