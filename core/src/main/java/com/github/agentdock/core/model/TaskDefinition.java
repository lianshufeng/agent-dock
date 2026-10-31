package com.github.agentdock.core.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.github.agentdock.core.task.TaskExecutionMode;

/**
 * 可由宿主注册的业务任务定义。任务定义只描述任务语义和执行边界，
 * 不包含容器、数据库或具体领域实现。
 */
public final class TaskDefinition {
    private String code;
    private String description;
    private Set<String> intentCodes = Set.of();
    private List<String> capabilityCodes = List.of();
    private Map<String, String> inputSchema = Map.of();
    private Map<String, CapabilitySchemaField> inputContract = Map.of();
    private Map<String, String> outputSchema = Map.of();
    private TaskExecutionMode executionMode = TaskExecutionMode.AGENT;
    private CapabilityRiskLevel riskLevel = CapabilityRiskLevel.READ_ONLY;
    private boolean confirmationRequired;
    private String successCriteria;
    /** 固定工具流程；输入引用 $task 表示本任务的上一步真实输出。 */
    private List<CapabilityInvocation> steps = List.of();

    public List<CapabilityInvocation> getSteps() { return steps; }
    public void setSteps(List<CapabilityInvocation> value) { steps = value == null ? List.of() : List.copyOf(value); }

    public TaskDefinition() {
    }

    public TaskDefinition(String code, String description, Set<String> intentCodes,
                          List<String> capabilityCodes, String successCriteria) {
        this.code = code;
        this.description = description;
        this.intentCodes = intentCodes == null ? Set.of() : Set.copyOf(intentCodes);
        this.capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
        this.successCriteria = successCriteria;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<String> getIntentCodes() { return intentCodes; }
    public void setIntentCodes(Set<String> intentCodes) { this.intentCodes = intentCodes == null ? Set.of() : Set.copyOf(intentCodes); }
    public List<String> getCapabilityCodes() { return capabilityCodes; }
    public void setCapabilityCodes(List<String> capabilityCodes) { this.capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes); }
    public Map<String, String> getInputSchema() { return inputSchema; }
    public Map<String, CapabilitySchemaField> getInputContract() { return inputContract; }
    public void setInputContract(Map<String, CapabilitySchemaField> value) {
        inputContract = value == null ? Map.of() : Map.copyOf(value);
    }
    public void setInputSchema(Map<String, String> inputSchema) { this.inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema); }
    public Map<String, String> getOutputSchema() { return outputSchema; }
    public void setOutputSchema(Map<String, String> outputSchema) { this.outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema); }
    public TaskExecutionMode getExecutionMode() { return executionMode; }
    public void setExecutionMode(TaskExecutionMode executionMode) { this.executionMode = executionMode == null ? TaskExecutionMode.AGENT : executionMode; }
    public CapabilityRiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(CapabilityRiskLevel riskLevel) { this.riskLevel = riskLevel == null ? CapabilityRiskLevel.READ_ONLY : riskLevel; }
    public boolean isConfirmationRequired() { return confirmationRequired; }
    public void setConfirmationRequired(boolean confirmationRequired) { this.confirmationRequired = confirmationRequired; }
    public String getSuccessCriteria() { return successCriteria; }
    public void setSuccessCriteria(String successCriteria) { this.successCriteria = successCriteria; }

    public boolean supports(String intentCode) {
        return intentCode != null && intentCodes.contains(intentCode);
    }

    public void validate() {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("任务编码不能为空");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("任务描述不能为空");
        if (intentCodes == null || intentCodes.isEmpty()) throw new IllegalArgumentException("任务至少需要关联一个意图");
        if (capabilityCodes == null) throw new IllegalArgumentException("任务能力目录不能为空");
        if (successCriteria == null || successCriteria.isBlank()) throw new IllegalArgumentException("任务完成标准不能为空");
        Objects.requireNonNull(executionMode, "任务执行模式不能为空");
        if (!steps.isEmpty()) {
            if (executionMode == TaskExecutionMode.AGENT)
                throw new IllegalArgumentException("固定步骤必须使用DIRECT或WORKFLOW模式");
            if (executionMode == TaskExecutionMode.DIRECT && steps.size() != 1)
                throw new IllegalArgumentException("DIRECT任务必须只有一个步骤");
            for (CapabilityInvocation step : steps) {
                if (step == null || !capabilityCodes.contains(step.getCapabilityCode()))
                    throw new IllegalArgumentException("固定步骤引用了任务能力边界外的工具");
            }
        }
    }
}
