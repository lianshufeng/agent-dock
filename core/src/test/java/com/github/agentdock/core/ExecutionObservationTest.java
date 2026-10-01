package com.github.agentdock.core;

import com.github.agentdock.core.capability.AiCapability;
import com.github.agentdock.core.event.AiEvent;
import com.github.agentdock.core.intent.LlmIntentAnalyzer;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionObservationTest {
    @Test void excessIndependentNodesWaitForNextBatch() {
        List<AiEvent> events = new CopyOnWriteArrayList<>();
        ConversationResult result = run(true, AiExecutionMode.PARALLEL, events, 25, false, 6);
        assertEquals(9, result.getIntentResults().size());
        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertTrue(events.stream().filter(event -> event.getType() == AiEventType.PLAN_SNAPSHOT).anyMatch(event -> {
            List<?> nodes = (List<?>) event.getPayload().get("nodes");
            long running = nodes.stream().filter(node -> "RUNNING".equals(((Map<?, ?>) node).get("status"))).count();
            boolean waiting = nodes.stream().anyMatch(node -> {
                Map<?, ?> item = (Map<?, ?>) node;
                return "PENDING".equals(item.get("status")) && ((List<?>) item.get("dependsOn")).isEmpty();
            });
            return running > 0 && running <= 4 && waiting;
        }));
    }
    @Test void serialNodesDoNotOverlap() {
        List<AiEvent> events = new CopyOnWriteArrayList<>();
        assertEquals(IntentStatus.SUCCESS, run(true, AiExecutionMode.SERIAL, events, 20).getStatus());
        assertFalse(event(events, "b", AiEventType.TASK_STARTED).getTimestamp()
                .isBefore(event(events, "a", AiEventType.TASK_SUCCESS).getTimestamp()));
        assertFalse(event(events, "c", AiEventType.TASK_STARTED).getTimestamp()
                .isBefore(event(events, "b", AiEventType.TASK_SUCCESS).getTimestamp()));
    }

    @Test void failedDependencyDoesNotChangeIndependentBranch() {
        List<AiEvent> events = new CopyOnWriteArrayList<>();
        ConversationResult result = run(true, AiExecutionMode.PARALLEL, events, 0, true);
        assertEquals(IntentStatus.FAILED, result.getIntentResults().stream().filter(item -> item.getIntentId().equals("a")).findFirst().orElseThrow().getStatus());
        assertEquals(IntentStatus.SUCCESS, result.getIntentResults().stream().filter(item -> item.getIntentId().equals("b")).findFirst().orElseThrow().getStatus());
        assertEquals(IntentStatus.SKIPPED, result.getIntentResults().stream().filter(item -> item.getIntentId().equals("c")).findFirst().orElseThrow().getStatus());
    }
    @Test void observationIsOptInAndDoesNotChangeBusinessResults() {
        List<AiEvent> original = new CopyOnWriteArrayList<>();
        List<AiEvent> observed = new CopyOnWriteArrayList<>();
        ConversationResult first = run(false, AiExecutionMode.SERIAL, original, 0);
        ConversationResult second = run(true, AiExecutionMode.SERIAL, observed, 0);
        assertEquals(first.getStatus(), second.getStatus());
        assertEquals(first.getMessage(), second.getMessage());
        assertEquals(original.stream().map(AiEvent::getType).toList(), observed.stream()
                .filter(event -> event.getType() != AiEventType.PLAN_SNAPSHOT).map(AiEvent::getType).toList());
        assertTrue(original.stream().noneMatch(event -> event.getType() == AiEventType.PLAN_SNAPSHOT));
        assertTrue(observed.stream().anyMatch(event -> event.getType() == AiEventType.PLAN_SNAPSHOT));
    }

    @Test void parallelSampleReportsDependenciesAndIndependentCompletion() throws Exception {
        List<AiEvent> events = new CopyOnWriteArrayList<>();
        // 可选真实长任务：-Dagentdock.observation.sampleMillis=10000，约 40 秒。
        long delay = Long.getLong("agentdock.observation.sampleMillis", 30);
        ConversationResult result = run(true, AiExecutionMode.PARALLEL, events, delay);
        assertEquals(IntentStatus.SUCCESS, result.getStatus());
        assertEquals(3, result.getIntentResults().size());
        AiEvent initial = events.stream().filter(event -> event.getType() == AiEventType.PLAN_SNAPSHOT).findFirst().orElseThrow();
        assertEquals("PARALLEL", initial.getPayload().get("executionMode"));
        assertEquals(3, ((List<?>) initial.getPayload().get("nodes")).size());
        AiEvent aEnd = event(events, "a", AiEventType.TASK_SUCCESS);
        AiEvent bEnd = event(events, "b", AiEventType.TASK_SUCCESS);
        AiEvent cStart = event(events, "c", AiEventType.TASK_STARTED);
        assertFalse(cStart.getTimestamp().isBefore(aEnd.getTimestamp()));
        assertFalse(cStart.getTimestamp().isBefore(bEnd.getTimestamp()));
        assertTrue(events.stream().filter(event -> event.getType() == AiEventType.PLAN_SNAPSHOT)
                .anyMatch(event -> event.getPayload().toString().contains("dependsOn=[a]")));
        List<Map<String, Object>> trace = new ArrayList<>();
        long sequence = 0;
        for (AiEvent event : events) {
            Map<String, Object> payload = new LinkedHashMap<>(event.getPayload());
            if (event.getIntentId() != null) payload.put("intentId", event.getIntentId());
            trace.add(Map.of("sequence", ++sequence, "type", event.getType().name(), "message", event.getMessage(),
                    "payload", payload, "progress", event.getProgress(), "occurredAt", event.getTimestamp().toEpochMilli()));
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/observation-events.json"),
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(trace), java.nio.charset.StandardCharsets.UTF_8);
    }

    private AiEvent event(List<AiEvent> events, String id, AiEventType type) {
        return events.stream().filter(event -> id.equals(event.getIntentId()) && event.getType() == type).findFirst().orElseThrow();
    }

    private ConversationResult run(boolean observation, AiExecutionMode mode, List<AiEvent> events, long delay) {
        return run(observation, mode, events, delay, false);
    }
    private ConversationResult run(boolean observation, AiExecutionMode mode, List<AiEvent> events, long delay, boolean failA) {
        return run(observation, mode, events, delay, failA, 0);
    }
    private ConversationResult run(boolean observation, AiExecutionMode mode, List<AiEvent> events, long delay, boolean failA, int additional) {
        AiKernel kernel = new AiKernel().registerExecutionMode(mode).registerExecutionObservation(observation)
                .registerIntent(new IntentDefinition("TASK", "样本任务"))
                .registerIntentAnalyzer(new LlmIntentAnalyzer() {
                    public IntentAdapterResult analyze(ConversationContext context, String catalog) {
                        List<IntentCandidate> intents = new ArrayList<>(List.of(intent("a", List.of()), intent("b", List.of()), intent("c", List.of("a"))));
                        for (int i = 0; i < additional; i++) intents.add(intent("extra-" + i, List.of()));
                        return new IntentAdapterResult(intents, ContextRequirement.NONE, null);
                    }
                }).registerCapability(new AiCapability() {
                    public CapabilityDefinition definition() {
                        return new CapabilityDefinition("MOCK", "可控耗时样本", Map.of(), Map.of(), false,
                                true, false, java.time.Duration.ofMinutes(2), 0, List.of()).terminal();
                    }
                    public CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context) {
                        if (failA && intent.getId().equals("a")) return CapabilityResult.failure("SAMPLE", "样本失败", false);
                        try { Thread.sleep(delay * (intent.getId().equals("b") ? 3 : 1)); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                        return CapabilityResult.success(intent.getDescription());
                    }
                }).registerIntentLoopPlanner(request -> {
                    IntentLoopDecision decision = new IntentLoopDecision();
                    decision.setStatus(IntentLoopDecision.Status.CONTINUE);
                    decision.setToolInvocations(List.of(new CapabilityInvocation("MOCK", Map.of())));
                    decision.setCompleteAfterTools(true); return decision;
                });
        kernel.subscribe(events::add);
        return kernel.execute(new ConversationContext("sample", UUID.randomUUID().toString(), "user",
                "执行长任务样本", List.of(), Map.of()));
    }
    private IntentCandidate intent(String id, List<String> dependencies) {
        IntentCandidate intent = new IntentCandidate(); intent.setId(id); intent.setCode("TASK");
        intent.setDescription("样本任务 " + id); intent.setExpectedResult(intent.getDescription());
        intent.setDependsOn(dependencies); intent.setConfidence(1); return intent;
    }
}
