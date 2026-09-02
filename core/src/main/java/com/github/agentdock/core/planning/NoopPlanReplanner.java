package com.github.agentdock.core.planning;

import java.util.Optional;

public final class NoopPlanReplanner implements PlanReplanner {
    @Override public Optional<PlanPatch> replan(PlanReplanRequest request) { return Optional.empty(); }
}
