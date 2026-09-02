package com.github.agentdock.core.planning;

import java.util.Optional;

/** 根据验证失败和新证据生成计划补丁；不得直接修改执行计划。 */
@FunctionalInterface
public interface PlanReplanner {
    Optional<PlanPatch> replan(PlanReplanRequest request);
}
