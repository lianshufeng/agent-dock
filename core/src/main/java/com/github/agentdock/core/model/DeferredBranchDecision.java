package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;

/** 条件分支判断及选中目标的新意图。 */
@Data
public final class DeferredBranchDecision {
    private String selectedChoiceId;
    private String reason;
    private List<IntentCandidate> candidates = List.of();
}
