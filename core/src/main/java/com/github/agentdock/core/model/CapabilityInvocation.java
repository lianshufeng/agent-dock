package com.github.agentdock.core.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;
import java.util.List;

/** LLM 为某个意图选择的具体能力调用，不属于意图本身的定义。 */
@Data
public class CapabilityInvocation {
    /** 从已注册工具目录中选择的能力编码。 */
    private String capabilityCode;
    /** 根据该工具 inputSchema 提取的调用参数。 */
    private Map<String, Object> arguments = Map.of();
    /** 执行规划阶段声明的前置结果绑定，意图识别阶段不生成。 */
    @JsonAlias("input_refs")
    private List<IntentInputReference> inputRefs = List.of();
    /** 工具级 DAG 节点标识及其前置调用标识；缺省时按规划返回顺序执行。 */
    private String invocationId;
    private List<String> dependsOnInvocationIds = List.of();

    public CapabilityInvocation() {
    }

    public CapabilityInvocation(String capabilityCode, Map<String, Object> arguments) {
        this.capabilityCode = capabilityCode;
        this.arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
