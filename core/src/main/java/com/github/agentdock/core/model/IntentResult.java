package com.github.agentdock.core.model;

/** 单个意图的最终执行结果。 */
import lombok.Data;
import com.github.agentdock.core.type.IntentStatus;
@Data
public class IntentResult {
    private String intentId; private String intentCode; private IntentStatus status; private Object output; private String message;
    public IntentResult() { }
    public IntentResult(String intentId,String intentCode,IntentStatus status,Object output,String message){this.intentId=intentId;this.intentCode=intentCode;this.status=status;this.output=output;this.message=message;}

    public static IntentResult success(IntentCandidate intent, Object output) {
        return new IntentResult(intent.getId(), intent.getCode(), IntentStatus.SUCCESS, output, null);
    }

    public static IntentResult failed(IntentCandidate intent, String message) {
        return new IntentResult(intent.getId(), intent.getCode(), IntentStatus.FAILED, null, message);
    }

    public static IntentResult skipped(IntentCandidate intent, String message) {
        return new IntentResult(intent.getId(), intent.getCode(), IntentStatus.SKIPPED, null, message);
    }
}
