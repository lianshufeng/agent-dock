package com.github.agentdock.core.planning;

import com.github.agentdock.core.model.IntentCandidate;
import lombok.Data;

import java.util.List;

/** 对当前计划的一项受控修改。 */
@Data
public final class PlanPatchOperation {
    private PlanPatchType type;
    private String targetNodeId;
    private IntentCandidate node;
    private List<String> dependsOn = List.of();
}
