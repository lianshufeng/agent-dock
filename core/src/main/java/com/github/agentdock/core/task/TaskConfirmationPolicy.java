package com.github.agentdock.core.task;

import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.Task;

/**
 * 宿主核实当前任务是否已被用户确认。默认不放行需要确认的任务。
 * 实现必须校验用户、会话、作用域和本次操作内容，不得仅信任客户端属性。
 * 本接口不替代业务权限、幂等或工具内的保存校验。
 */
@FunctionalInterface
public interface TaskConfirmationPolicy {
    boolean isConfirmed(Task task, AiExecutionContext context);
}
