package com.github.agentdock.core.model;

import java.util.List;

/** 多意图识别、依赖排序后的串行执行计划。 */
import lombok.Data;

@Data
public class IntentAnalysis {
    private List<IntentCandidate> orderedIntents; private ContextRequirement contextRequirement; private String clarificationQuestion;
    public IntentAnalysis() { }
    public IntentAnalysis(List<IntentCandidate> orderedIntents, ContextRequirement contextRequirement, String clarificationQuestion) {
        this.orderedIntents=orderedIntents==null?List.of():List.copyOf(orderedIntents); this.contextRequirement=contextRequirement==null?ContextRequirement.NONE:contextRequirement; this.clarificationQuestion=clarificationQuestion;
    }

    public boolean requiresClarification() {
        return clarificationQuestion != null && !clarificationQuestion.isBlank();
    }
}
