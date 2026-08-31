package com.github.agentdock.core.aggregation.impl;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.aggregation.ResultAggregator;
import java.util.List;
import com.github.agentdock.core.type.IntentStatus;

public class DefaultResultAggregator implements ResultAggregator {
    @Override
    public ConversationResult aggregate(List<IntentResult> results) {
        boolean failed = results.stream().anyMatch(r -> r.getStatus() == IntentStatus.FAILED);
        boolean waiting = results.stream().anyMatch(r -> r.getStatus() == IntentStatus.WAITING_USER);
        IntentStatus status = waiting ? IntentStatus.WAITING_USER : failed ? IntentStatus.FAILED : IntentStatus.SUCCESS;
        String message = failed ? "部分意图执行失败" : waiting ? "等待补充信息" : "处理完成";
        return new ConversationResult(status, results, message);
    }
}
