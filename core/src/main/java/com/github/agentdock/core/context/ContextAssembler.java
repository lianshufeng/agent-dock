package com.github.agentdock.core.context;

/** 将分散的会话、计划、观察和验证证据收敛为受预算控制的上下文快照。 */
@FunctionalInterface
public interface ContextAssembler {
    ContextSnapshot assemble(ContextRequest request);
}
