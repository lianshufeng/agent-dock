package com.github.agentdock.core.aggregation.impl;

import com.github.agentdock.core.aggregation.ResultSummarizer;
import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.IntentStatus;

import java.util.stream.Collectors;

/** 不解释宿主输出字段的确定性结果汇总器。 */
public final class DefaultResultSummarizer implements ResultSummarizer {
    @Override
    public String summarize(ConversationContext context, ConversationResult result) {
        if (result.getIntentResults() == null || result.getIntentResults().isEmpty()) {
            return result.getMessage() == null ? result.getStatus().name() : result.getMessage();
        }
        if (result.getIntentResults().size() == 1) {
            return value(result.getIntentResults().get(0));
        }
        return result.getIntentResults().stream().map(this::line).distinct()
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String line(IntentResult result) {
        return "- " + value(result);
    }

    private String value(IntentResult result) {
        if (result.getStatus() != IntentStatus.SUCCESS) {
            return result.getMessage() == null ? result.getStatus().name() : result.getMessage();
        }
        Object output = result.getOutput();
        return output == null ? result.getStatus().name() : String.valueOf(output);
    }
}
