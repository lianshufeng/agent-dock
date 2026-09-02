package com.github.agentdock.core.task;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.IntentResult;
import com.github.agentdock.core.model.Task;
import com.github.agentdock.core.model.TaskVerification;
import java.util.List;

/** 按顺序组合通用、契约和宿主业务验收器；第一个失败结果决定任务不通过。 */
public final class CompositeTaskVerifier implements TaskVerifier {
    private final List<TaskVerifier> verifiers;

    public CompositeTaskVerifier(List<TaskVerifier> verifiers) {
        this.verifiers = verifiers == null ? List.of() : verifiers.stream()
                .filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public TaskVerification verify(Task task, IntentResult result, AiExecutionContext context) {
        for (TaskVerifier verifier : verifiers) {
            TaskVerification verification = verifier.verify(task, result, context);
            if (verification == null) return TaskVerification.failed("任务验收器未返回结果", true);
            if (!verification.isPassed()) return verification;
        }
        return TaskVerification.passed();
    }
}
