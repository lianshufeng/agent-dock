package com.github.agentdock.core.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Tool/Skill 对 LLM 和执行器公开的能力声明。 */
import lombok.Data;
@Data
public class CapabilityDefinition {
    /** 全局唯一能力编码，也是意图执行时的默认路由键。 */
    private String code;
    /** 提供给 LLM 的能力说明。 */
    private String description;
    /** 提供给 LLM 的入参与出参结构声明。 */
    private Map<String,String> inputSchema;
    private Map<String,String> outputSchema;
    /** 类型化契约；为空时兼容读取旧版字符串 Schema。 */
    private Map<String, CapabilitySchemaField> inputContract = Map.of();
    private Map<String, CapabilitySchemaField> outputContract = Map.of();
    /** 是否会修改外部状态；有副作用的能力重试时需谨慎。 */
    private boolean sideEffect;
    /** 是否具备幂等语义；有副作用能力只有幂等时才允许自动重试。 */
    private boolean idempotent;
    /** 执行失败后是否支持补偿操作。 */
    private boolean compensatable;
    /** 成功结果是否已可直接作为当前意图最终结果，避免无意义的下一轮规划。 */
    private boolean terminalResult;
    /** 宿主声明的能力风险和规划自由度；核心只执行通用约束，不解释业务编码。 */
    private CapabilityRiskLevel riskLevel = CapabilityRiskLevel.READ_ONLY;
    private CapabilityExecutionMode executionMode = CapabilityExecutionMode.AGENT;
    /** 单次调用超时时间。 */
    private Duration timeout;
    /** 主能力失败后的最大重试次数。 */
    private int maxRetries;
    /** 主能力不可用时可依次尝试的替代能力编码。 */
    private List<String> fallbackCapabilities;
    public CapabilityDefinition() { }
    public CapabilityDefinition(String code,String description,
                                Map<String,String> inputSchema,Map<String,String> outputSchema,
                                boolean sideEffect,boolean idempotent,boolean compensatable,
                                Duration timeout,int maxRetries,List<String> fallbackCapabilities){
        this.code=code;this.description=description;
        this.inputSchema=inputSchema==null?Map.of():Map.copyOf(inputSchema);
        this.outputSchema=outputSchema==null?Map.of():Map.copyOf(outputSchema);
        this.sideEffect=sideEffect;this.idempotent=idempotent;this.compensatable=compensatable;
        this.riskLevel=sideEffect ? CapabilityRiskLevel.REVERSIBLE_WRITE : CapabilityRiskLevel.READ_ONLY;
        this.timeout=timeout==null?Duration.ofSeconds(30):timeout;this.maxRetries=Math.max(maxRetries,0);
        this.fallbackCapabilities=fallbackCapabilities==null?List.of():List.copyOf(fallbackCapabilities);
    }

    public CapabilityDefinition terminal() {
        this.terminalResult = true;
        return this;
    }

    public Map<String, CapabilitySchemaField> effectiveInputContract() {
        return effectiveContract(inputContract, inputSchema);
    }

    public CapabilityDefinition risk(CapabilityRiskLevel value) {
        this.riskLevel = value == null ? CapabilityRiskLevel.READ_ONLY : value;
        return this;
    }

    public CapabilityDefinition executionMode(CapabilityExecutionMode value) {
        this.executionMode = value == null ? CapabilityExecutionMode.AGENT : value;
        return this;
    }

    public Map<String, CapabilitySchemaField> effectiveOutputContract() {
        return effectiveContract(outputContract, outputSchema);
    }

    private Map<String, CapabilitySchemaField> effectiveContract(Map<String, CapabilitySchemaField> typedContract,
                                                                  Map<String, String> legacySchema) {
        if (typedContract != null && !typedContract.isEmpty()) return typedContract;
        Map<String, CapabilitySchemaField> result = new java.util.LinkedHashMap<>();
        if (legacySchema != null) legacySchema.forEach((name, type) -> {
            String value = type == null ? "string" : type.trim();
            if (value.toLowerCase(java.util.Locale.ROOT).startsWith("enum:")) {
                CapabilitySchemaField field = new CapabilitySchemaField("string", false);
                field.setEnumValues(java.util.Arrays.stream(value.substring(value.indexOf(':') + 1).split("\\|"))
                        .map(String::trim).filter(item -> !item.isEmpty()).toList());
                result.put(name, field);
            } else {
                result.put(name, new CapabilitySchemaField(value, false));
            }
        });
        return Map.copyOf(result);
    }

}
