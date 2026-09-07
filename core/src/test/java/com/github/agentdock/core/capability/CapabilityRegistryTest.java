package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.CapabilityResult;
import com.github.agentdock.core.model.IntentCandidate;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityRegistryTest {

    @Test
    void rejectsInvalidAndDuplicateCapabilities() {
        CapabilityRegistry registry = new CapabilityRegistry();
        AiCapability capability = capability("knowledge.search");

        assertThrows(IllegalArgumentException.class, () -> registry.register(null));
        registry.register(capability);
        assertThrows(IllegalArgumentException.class, () -> registry.register(capability("knowledge.search")));

        assertEquals(capability, registry.find("knowledge.search"));
        assertEquals(1, registry.definitions().size());
    }

    private AiCapability capability(String code) {
        CapabilityDefinition definition = new CapabilityDefinition(code, "查询知识库", Map.of(), Map.of(),
                false, true, false, null, 0, null);
        return new AiCapability() {
            @Override
            public CapabilityDefinition definition() { return definition; }

            @Override
            public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                return CapabilityResult.success("ok");
            }
        };
    }
}
