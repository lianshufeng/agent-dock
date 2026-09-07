package com.github.agentdock.core.intent;

import com.github.agentdock.core.model.IntentDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntentRegistryTest {

    @Test
    void rejectsInvalidAndDuplicateDefinitions() {
        IntentRegistry registry = new IntentRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new IntentDefinition("", "查询")));
        registry.register(new IntentDefinition("knowledge.search", "查询知识库"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(new IntentDefinition("knowledge.search", "再次查询")));

        assertEquals(1, registry.definitions().size());
        assertEquals("knowledge.search", registry.find("knowledge.search").getCode());
    }
}
