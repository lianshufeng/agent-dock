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
}
