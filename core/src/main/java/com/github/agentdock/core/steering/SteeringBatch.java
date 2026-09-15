package com.github.agentdock.core.steering;

import java.util.List;

public record SteeringBatch(List<SteeringInput> inputs) {
    public static final SteeringBatch EMPTY = new SteeringBatch(List.of());

    public SteeringBatch {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }

    public boolean isEmpty() { return inputs.isEmpty(); }
}
