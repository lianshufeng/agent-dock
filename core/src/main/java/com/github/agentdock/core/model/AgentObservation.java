package com.github.agentdock.core.model;

import java.util.Map;

/** Agent 对一次工具调用结果的观察。 */
import lombok.Data;
@Data
public class AgentObservation { private String toolCode; private Map<String,Object> arguments; private CapabilityResult result; public AgentObservation(){} public AgentObservation(String toolCode,Map<String,Object> arguments,CapabilityResult result){this.toolCode=toolCode;this.arguments=arguments;this.result=result;} }
