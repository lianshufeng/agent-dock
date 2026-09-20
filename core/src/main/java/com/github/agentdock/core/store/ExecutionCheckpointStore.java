package com.github.agentdock.core.store;

import java.util.Optional;

/** 可选执行检查点存储。内容是核心库生成的 JSON 快照，宿主负责持久化和会话隔离。 */
public interface ExecutionCheckpointStore {
    Optional<String> load(String conversationId, String executionId);
    void save(String conversationId, String executionId, String snapshot);
}
