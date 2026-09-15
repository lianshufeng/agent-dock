package com.github.agentdock.core.steering;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionSteeringTest {
    @Test
    void preservesOrderAndDeduplicatesAcceptedInputs() {
        ExecutionSteering steering = new ExecutionSteering(2);
        SteeringInput first = new SteeringInput("one", "第一条", 1, Map.of());
        SteeringInput second = new SteeringInput("two", "第二条", 2, Map.of());

        assertEquals(SteeringOfferResult.ACCEPTED, steering.offer(first));
        assertEquals(SteeringOfferResult.DUPLICATE, steering.offer(first));
        assertEquals(SteeringOfferResult.ACCEPTED, steering.offer(second));
        assertEquals(java.util.List.of(first, second), steering.drain().inputs());
    }

    @Test
    void atomicallySealsOnlyWhenNoInputIsPending() {
        ExecutionSteering steering = new ExecutionSteering();
        assertEquals(SteeringOfferResult.ACCEPTED,
                steering.offer(new SteeringInput("one", "补充", 1, Map.of())));
        assertFalse(steering.sealIfEmpty());
        steering.drain();
        assertTrue(steering.sealIfEmpty());
        assertFalse(steering.isAccepting());
        assertEquals(SteeringOfferResult.CLOSED,
                steering.offer(new SteeringInput("two", "太晚到达", 2, Map.of())));
    }
}
