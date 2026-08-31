package com.github.agentdock.core.task;

import com.github.agentdock.core.model.*;
import java.util.Optional;

public final class NoopTaskReplanner implements TaskReplanner {
    @Override public Optional<Task> replan(Task task, TaskVerification verification, AiExecutionContext context) {
        // 默认实现必须保持真正的 no-op；宿主如需重规划应显式注册实现并维护调用账本。
        return Optional.empty();
    }
}
