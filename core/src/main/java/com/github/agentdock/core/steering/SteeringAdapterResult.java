package com.github.agentdock.core.steering;

import com.github.agentdock.core.model.IntentCandidate;
import lombok.Data;

import java.util.List;

/** 模型对执行中补充输入给出的结构化计划变更建议。 */
@Data
public final class SteeringAdapterResult {
    private SteeringAction action = SteeringAction.ADD;
    private List<String> targetNodeIds = List.of();
    private List<IntentCandidate> candidates = List.of();
    private String reason;
    private String clarificationQuestion;
}
