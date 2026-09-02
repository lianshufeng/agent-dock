package com.github.agentdock.core.planning;

import lombok.Data;
import java.util.List;

/** 重规划器建议的原子计划补丁。 */
@Data
public final class PlanPatch {
    private List<PlanPatchOperation> operations = List.of();
    private String reason;
}
