package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;

/** 前置意图取得结果后才能选择的互斥分支。 */
@Data
public final class DeferredBranch {
    private String id;
    private String triggerIntentId;
    private List<Choice> choices = List.of();
    private String selectedChoiceId;
    private List<String> addedNodeIds = List.of();
    private String resolution;
    private String resolutionReason;

    @Data
    public static final class Choice {
        private String id;
        private String condition;
        private String goal;
    }
}
