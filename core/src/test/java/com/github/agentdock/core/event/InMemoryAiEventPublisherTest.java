package com.github.agentdock.core.event;

import com.github.agentdock.core.event.impl.InMemoryAiEventPublisher;
import com.github.agentdock.core.type.AiEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryAiEventPublisherTest {

    @Test
    void isolatesSubscriberFailureAndPreservesOrder() {
        InMemoryAiEventPublisher publisher = new InMemoryAiEventPublisher();
        List<String> received = new ArrayList<>();
        publisher.subscribe(event -> { throw new IllegalStateException("订阅者失败"); });
        publisher.subscribe(event -> received.add(event.getMessage()));

        publisher.publish(event("第一"));
        publisher.publish(event("第二"));

        assertEquals(List.of("第一", "第二"), received);
    }

    @Test
    void rejectsNullSubscriber() {
        InMemoryAiEventPublisher publisher = new InMemoryAiEventPublisher();

        assertThrows(NullPointerException.class, () -> publisher.subscribe(null));
    }

    private AiEvent event(String message) {
        return new AiEvent("event-" + message, "conversation", "execution", null,
                AiEventType.TASK_STARTED, message, 0, null, null);
    }
}
