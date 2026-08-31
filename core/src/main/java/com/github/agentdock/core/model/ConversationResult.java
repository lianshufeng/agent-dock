package com.github.agentdock.core.model;

import java.util.List;

/** AI 内核交付给接口层的统一结果。 */
import lombok.Data;
import com.github.agentdock.core.type.IntentStatus;
@Data
public class ConversationResult {
    private IntentStatus status; private List<IntentResult> intentResults; private String message;
    public ConversationResult() { }
    public ConversationResult(IntentStatus status,List<IntentResult> intentResults,String message){this.status=status;this.intentResults=intentResults==null?List.of():List.copyOf(intentResults);this.message=message;}
}
