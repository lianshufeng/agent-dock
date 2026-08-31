package com.github.agentdock.core.task;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.IntentStatus;

/** 默认验证器：先检查执行状态和非空结果，复杂领域规则由宿主注入。 */
public final class DefaultTaskVerifier implements TaskVerifier {
    @Override
    public TaskVerification verify(Task task, IntentResult result, AiExecutionContext context) {
        if (result == null || result.getStatus() != IntentStatus.SUCCESS)
            return TaskVerification.failed(result == null ? "任务未返回结果" : result.getMessage(), true);
        if (result.getOutput() == null)
            return TaskVerification.failed("任务结果为空", true);
        return TaskVerification.passed();
    }
}
