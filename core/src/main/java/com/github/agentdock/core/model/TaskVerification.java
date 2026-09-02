package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/** 能力返回后对任务成功标准的检查结果。 */
@Data
public class TaskVerification {
    private boolean passed;
    private String message;
    private boolean replanRequired;
    private VerificationAction action = VerificationAction.PASS;
    private List<Map<String, Object>> evidence = List.of();

    public TaskVerification() { }
    public TaskVerification(boolean passed, String message, boolean replanRequired) {
        this.passed = passed; this.message = message; this.replanRequired = replanRequired;
        this.action = passed ? VerificationAction.PASS
                : replanRequired ? VerificationAction.REPLAN : VerificationAction.FAIL;
    }
    public static TaskVerification passed() { return new TaskVerification(true, null, false); }
    public static TaskVerification failed(String message, boolean replan) { return new TaskVerification(false, message, replan); }

    public static TaskVerification of(VerificationAction action, String message,
                                      List<Map<String, Object>> evidence) {
        VerificationAction normalized = action == null ? VerificationAction.FAIL : action;
        TaskVerification result = new TaskVerification(normalized == VerificationAction.PASS, message,
                normalized == VerificationAction.REPLAN);
        result.setAction(normalized);
        result.setEvidence(evidence == null ? List.of() : List.copyOf(evidence));
        return result;
    }
}
