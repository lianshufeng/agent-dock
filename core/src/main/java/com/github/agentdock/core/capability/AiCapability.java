package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;

/** AI 内核可注册、规划和执行的统一能力接口。 */
public interface AiCapability {
    /** 返回能力编码、输入输出、重试和替代能力等元数据。 */
    CapabilityDefinition definition();

    /** 使用标准意图参数执行能力，不得依赖具体的容器实现。 */
    CapabilityResult invoke(IntentCandidate intent, AiExecutionContext context);

    /**
     * 对已经产生的副作用执行补偿；只有契约声明 compensatable 时才会被内核调用。
     * 支持补偿的宿主能力必须覆盖此方法并实现真实回滚行为。
     */
    default CapabilityResult compensate(IntentCandidate intent, AiExecutionContext context,
                                        CapabilityInvocation invocation, CapabilityResult failedResult) {
        return CapabilityResult.failure("COMPENSATION_NOT_IMPLEMENTED", "能力未实现补偿操作", false);
    }

    /**
     * 是否支持核实未知的副作用结果。默认关闭；宿主只能在具备权威读回或稳定幂等键时启用。
     */
    default boolean supportsUnknownOutcomeRecovery() {
        return false;
    }

    /**
     * 核实或按同一稳定幂等键安全重放一次未知结果。只有返回成功且输出通过契约校验，内核才会确认成功；
     * 返回失败、未知或发生异常都仍按未知结果处理。该方法在副作用串行执行器中运行。
     */
    default CapabilityResult recoverUnknownOutcome(IntentCandidate intent, AiExecutionContext context,
                                                   CapabilityInvocation invocation,
                                                   CapabilityResult previousUnknown) {
        return CapabilityResult.unknown("该能力未实现未知写入结果核实");
    }
}
