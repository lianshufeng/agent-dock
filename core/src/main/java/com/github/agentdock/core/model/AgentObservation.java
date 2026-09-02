package com.github.agentdock.core.model;

import java.util.Map;

/** Agent 对一次工具调用结果的观察。 */
import lombok.Data;
@Data
public class AgentObservation {
    private String toolCode;
    private String requestedToolCode;
    private Map<String,Object> arguments;
    private CapabilityResult result;
    private int recoveryAttempts;
    private boolean compensationAttempted;
    private long elapsedMillis;
    public AgentObservation() { }
    public AgentObservation(String toolCode, Map<String,Object> arguments, CapabilityResult result) {
        this(toolCode, toolCode, arguments, result, 0, false, 0L);
    }
    public AgentObservation(String toolCode, String requestedToolCode, Map<String,Object> arguments,
                            CapabilityResult result, int recoveryAttempts,
                            boolean compensationAttempted, long elapsedMillis) {
        this.toolCode=toolCode; this.requestedToolCode=requestedToolCode; this.arguments=arguments;
        this.result=result; this.recoveryAttempts=recoveryAttempts;
        this.compensationAttempted=compensationAttempted; this.elapsedMillis=elapsedMillis;
    }
}
