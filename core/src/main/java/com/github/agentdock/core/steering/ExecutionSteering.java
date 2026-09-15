package com.github.agentdock.core.steering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次执行的线程安全输入通道。宿主持有该对象并投递输入，执行引擎只在安全节点批量消费。
 */
public final class ExecutionSteering {
    private static final int DEFAULT_CAPACITY = 100;
    private static final ExecutionSteering DISABLED = new ExecutionSteering(0, false);

    private final int capacity;
    private final Map<String, SteeringInput> pending = new LinkedHashMap<>();
    private final java.util.Set<String> accepted = new java.util.HashSet<>();
    private boolean accepting;

    public ExecutionSteering() { this(DEFAULT_CAPACITY, true); }

    public ExecutionSteering(int capacity) { this(capacity, true); }

    private ExecutionSteering(int capacity, boolean accepting) {
        if (capacity < 0) throw new IllegalArgumentException("输入队列容量不能为负数");
        this.capacity = capacity;
        this.accepting = accepting;
    }

    public static ExecutionSteering disabled() { return DISABLED; }

    public synchronized SteeringOfferResult offer(SteeringInput input) {
        if (input == null) throw new IllegalArgumentException("插入输入不能为空");
        if (accepted.contains(input.inputId())) return SteeringOfferResult.DUPLICATE;
        if (!accepting) return SteeringOfferResult.CLOSED;
        if (pending.size() >= capacity) return SteeringOfferResult.FULL;
        pending.put(input.inputId(), input);
        accepted.add(input.inputId());
        return SteeringOfferResult.ACCEPTED;
    }

    public synchronized SteeringBatch drain() {
        if (pending.isEmpty()) return SteeringBatch.EMPTY;
        List<SteeringInput> values = List.copyOf(pending.values());
        pending.clear();
        return new SteeringBatch(values);
    }

    /** 队列为空时原子关闭；有并发输入到达时保持开启并返回 false。 */
    public synchronized boolean sealIfEmpty() {
        if (!pending.isEmpty()) return false;
        accepting = false;
        return true;
    }

    public synchronized boolean isAccepting() { return accepting; }
}
