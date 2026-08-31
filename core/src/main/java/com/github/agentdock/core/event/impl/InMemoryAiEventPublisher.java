package com.github.agentdock.core.event.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import com.github.agentdock.core.event.AiEvent;
import com.github.agentdock.core.event.AiEventPublisher;

/** 单实例默认实现；后续可替换为 Redis Stream 等跨节点实现。 */
public class InMemoryAiEventPublisher implements AiEventPublisher {
    private final List<Consumer<AiEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Object[] executionLocks = new Object[64];

    public InMemoryAiEventPublisher() {
        java.util.Arrays.setAll(executionLocks, ignored -> new Object());
    }

    @Override
    public void publish(AiEvent event) {
        String executionId = event == null ? null : event.getExecutionId();
        Object lock = executionLocks[Math.floorMod(java.util.Objects.hashCode(executionId), executionLocks.length)];
        synchronized (lock) {
            subscribers.forEach(subscriber -> subscriber.accept(event));
        }
    }
    public void subscribe(Consumer<AiEvent> subscriber) { subscribers.add(subscriber); }
    public void unsubscribe(Consumer<AiEvent> subscriber) { subscribers.remove(subscriber); }
}
