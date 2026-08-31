package com.github.agentdock.core.task;

import com.github.agentdock.core.model.*;
import java.util.Optional;

/** 根据任务验证结果决定是否生成补救任务；默认实现不擅自重规划。 */
public interface TaskReplanner {
    Optional<Task> replan(Task task, TaskVerification verification, AiExecutionContext context);
}
