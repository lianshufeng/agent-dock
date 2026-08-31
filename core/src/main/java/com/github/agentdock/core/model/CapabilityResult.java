package com.github.agentdock.core.model;

/** 一次 Tool/Skill 调用的标准结果。 */
import lombok.Data;
@Data
public class CapabilityResult {
    private boolean success; private Object output; private String errorCode; private String message; private boolean retryable;
    public CapabilityResult() { }
    public CapabilityResult(boolean success,Object output,String errorCode,String message,boolean retryable){this.success=success;this.output=output;this.errorCode=errorCode;this.message=message;this.retryable=retryable;}

    public static CapabilityResult success(Object output) {
        return new CapabilityResult(true, output, null, null, false);
    }

    public static CapabilityResult failure(String errorCode, String message, boolean retryable) {
        return new CapabilityResult(false, null, errorCode, message, retryable);
    }
}
