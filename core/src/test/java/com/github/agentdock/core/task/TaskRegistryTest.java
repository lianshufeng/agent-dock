package com.github.agentdock.core.task;

import com.github.agentdock.core.model.IntentCandidate;
import com.github.agentdock.core.model.Task;
import com.github.agentdock.core.model.TaskDefinition;
import com.github.agentdock.core.capability.CapabilityRegistry;
import com.github.agentdock.core.intent.IntentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TaskRegistryTest {
    @Test
    void registersAndFindsTaskByIntent() {
        TaskRegistry registry = new TaskRegistry();
        registry.register(new TaskDefinition("knowledge.list", "分页查询知识资源",
                Set.of("LOCATE"), List.of("knowledge.search"), "返回当前页资源和总数"));

        assertEquals(1, registry.findByIntent("LOCATE").size());
        IntentCandidate intent = new IntentCandidate();
        intent.setId("intent-1");
        intent.setCode("LOCATE");
        intent.setDescription("分页查询知识资源");
        Task task = new RegistryTaskPlanner(registry).plan(intent, null);
        assertEquals("分页查询知识资源", task.getObjective());
        assertEquals("knowledge.list", task.getDefinitionCode());
        assertEquals(List.of("knowledge.search"), task.getAllowedCapabilityCodes());
    }

    @Test
    void rejectsInvalidAndDuplicateDefinitions() {
        TaskRegistry registry = new TaskRegistry();
        TaskDefinition invalid = new TaskDefinition();
        assertThrows(IllegalArgumentException.class, () -> registry.register(invalid));

        TaskDefinition definition = new TaskDefinition("chat.reply", "回复用户",
                Set.of("COMMUNICATE"), List.of("chat.reply"), "返回非空文本");
        registry.register(definition);
        assertThrows(IllegalArgumentException.class, () -> registry.register(definition));
    }

    @Test
    void fallsBackToLegacyTaskWhenIntentHasNoRegisteredTask() {
        TaskRegistry registry = new TaskRegistry();
        IntentCandidate intent = new IntentCandidate();
        intent.setId("intent-2");
        intent.setCode("UNDERSTAND");
        intent.setDescription("解释内容");
        intent.setExpectedResult("返回解释");

        Task task = new RegistryTaskPlanner(registry).plan(intent, null);
        assertEquals("解释内容", task.getObjective());
        assertEquals("返回解释", task.getSuccessCriteria());
    }

    @Test
    void validatesIntentAndCapabilityReferencesBeforeExecution() {
        TaskRegistry registry = new TaskRegistry();
        registry.register(new TaskDefinition("knowledge.detail", "读取详情",
                Set.of("LOCATE"), List.of("knowledge.detail"), "返回详情"));
        assertThrows(IllegalStateException.class,
                () -> registry.validateReferences(new IntentRegistry(), new CapabilityRegistry()));
    }

    @Test
    void selectorChoosesAmongMultipleTasksWithoutGuessingRegistrationOrder() {
        TaskRegistry registry = new TaskRegistry();
        registry.register(new TaskDefinition("list", "列表", Set.of("LOCATE"), List.of("list"), "返回列表"));
        registry.register(new TaskDefinition("detail", "详情", Set.of("LOCATE"), List.of("detail"), "返回详情"));
        IntentCandidate intent = new IntentCandidate();
        intent.setId("intent-3"); intent.setCode("LOCATE"); intent.setDescription("查看详情");
        Task task = new RegistryTaskPlanner(registry, null,
                (candidate, context, definitions) -> "detail").plan(intent, null);
        assertEquals("detail", task.getDefinitionCode());
        assertEquals(List.of("detail"), task.getAllowedCapabilityCodes());
        assertNull(new RegistryTaskPlanner(registry).plan(intent, null).getDefinitionCode());
        assertThrows(IllegalArgumentException.class, () -> new RegistryTaskPlanner(registry, null,
                (candidate, context, definitions) -> "unknown").plan(intent, null));
    }
}
