package com.github.agentdock.core.model;

import lombok.Data;

/** 能力返回后对任务成功标准的检查结果。 */
@Data
public class TaskVerification {
    private boolean passed;
    private String message;
    private boolean replanRequired;

    public TaskVerification() { }
    public TaskVerification(boolean passed, String message, boolean replanRequired) {
        this.passed = passed; this.message = message; this.replanRequired = replanRequired;
    }
    public static TaskVerification passed() { return new TaskVerification(true, null, false); }
    public static TaskVerification failed(String message, boolean replan) { return new TaskVerification(false, message, replan); }
}
