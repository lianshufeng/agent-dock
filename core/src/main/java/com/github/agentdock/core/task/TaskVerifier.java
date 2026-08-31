package com.github.agentdock.core.task;

import com.github.agentdock.core.model.*;

/** 对任务结果执行与能力无关的成功标准检查。 */
public interface TaskVerifier {
    TaskVerification verify(Task task, IntentResult result, AiExecutionContext context);
}
