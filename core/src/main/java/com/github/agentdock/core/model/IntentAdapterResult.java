package com.github.agentdock.core.model;

import java.util.List;

/** 单个意图适配器的分析结果。 */
import lombok.Data;
@Data
public class IntentAdapterResult {
    private List<IntentCandidate> candidates; private ContextRequirement contextRequirement; private String clarificationQuestion;
    private List<DeferredBranch> deferredBranches = List.of();
    public IntentAdapterResult() { }
    public IntentAdapterResult(List<IntentCandidate> candidates, ContextRequirement contextRequirement, String clarificationQuestion) { this.candidates=candidates==null?List.of():List.copyOf(candidates); this.contextRequirement=contextRequirement==null?ContextRequirement.NONE:contextRequirement; this.clarificationQuestion=clarificationQuestion; }

    public static IntentAdapterResult empty() {
        return new IntentAdapterResult(List.of(), ContextRequirement.NONE, null);
    }

}
