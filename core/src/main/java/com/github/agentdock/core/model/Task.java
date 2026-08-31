package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** 意图拆解后的可执行工作单元；不绑定具体能力。 */
@Data
public class Task {
    private String id;
    private String intentId;
    private String objective;
    private String successCriteria;
    private List<String> dependsOn = List.of();
    private Map<String, Object> inputBindings = Map.of();
    /** 任务阶段选择能力后的调用描述；意图本身不再承载该字段。 */
    private CapabilityInvocation capabilityInvocation;
    private TaskStatus status = TaskStatus.PENDING;

    public Task() { }
    public Task(String id, String intentId, String objective, String successCriteria, List<String> dependsOn) {
        this.id = id; this.intentId = intentId; this.objective = objective;
        this.successCriteria = successCriteria;
        this.dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
