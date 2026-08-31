package com.github.agentdock.core.model;

import java.util.Map;

/** Agent 每轮规划后给出的下一步动作。 */
import lombok.Data;
@Data
public class AgentDecision {
    private Type type; private String toolCode; private Map<String,Object> arguments; private Object finalOutput; private String summary;
    public AgentDecision() { }
    public AgentDecision(Type type,String toolCode,Map<String,Object> arguments,Object finalOutput,String summary){this.type=type;this.toolCode=toolCode;this.arguments=arguments==null?Map.of():Map.copyOf(arguments);this.finalOutput=finalOutput;this.summary=summary;}
    public enum Type { TOOL_CALL, COMPLETE, FAIL }

}
