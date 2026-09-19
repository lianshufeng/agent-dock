package com.github.agentdock.core.provider;

import com.github.agentdock.core.provider.schema.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 执行中输入的受限计划变更判断。 */
public final class SteeringAnalysisContract implements StructuredOutputContract {
    public static final SteeringAnalysisContract INSTANCE = new SteeringAnalysisContract();
    private final Map<SchemaField, SchemaObjectValue> fields = new LinkedHashMap<>();

    private SteeringAnalysisContract() {
        fields.put(new SchemaField("action", "对原计划的影响"),
                new SchemaEnumValue("只能返回指定枚举值", "ADD", "REPLACE", "UPDATE", "CANCEL", "NOOP"));
        fields.put(new SchemaField("targetNodeIds", "ADD 时为应等待新增任务的原节点；REPLACE/UPDATE/CANCEL 时为受影响节点"),
                new SchemaArrayValue("仅引用当前计划中明确受影响的未执行节点",
                        new SchemaStringValue("当前计划节点 ID")));
        Map<SchemaField, SchemaObjectValue> candidate = new LinkedHashMap<>();
        candidate.put(new SchemaField("id", "本次新增意图的唯一 ID"), new SchemaStringValue("不可与已有节点 ID 重复"));
        candidate.put(new SchemaField("code", "宿主意图编码"), new SchemaStringValue("必须取自意图目录"));
        candidate.put(new SchemaField("description", "具体目标"), new SchemaStringValue("简要说明任务"));
        candidate.put(new SchemaField("progressText", "面向用户的进度文案"), new SchemaStringValue("简短描述"));
        candidate.put(new SchemaField("expectedResult", "可判断的预期结果"), new SchemaStringValue("明确完成标准"));
        candidate.put(new SchemaField("confidence", "置信度"), new SchemaNumberValue("0 到 1"));
        candidate.put(new SchemaField("priority", "执行优先级"), new SchemaIntValue("非负整数"));
        candidate.put(new SchemaField("dependsOn", "前置意图 ID"),
                new SchemaArrayValue("可引用本次新增意图或已有计划中有效节点",
                        new SchemaStringValue("意图实例 ID")));
        candidate.put(new SchemaField("contextRequirement", "需要召回的上下文"),
                IntentAnalysisContract.INSTANCE.fields().entrySet().stream()
                        .filter(entry -> entry.getKey().name().equals("contextRequirement"))
                        .findFirst().orElseThrow().getValue());
        fields.put(new SchemaField("candidates", "仅本次新增或替换所需的意图，不重复生成原计划节点"),
                new SchemaArrayValue("本次新增意图", new SchemaObjectValue(candidate)));
        fields.put(new SchemaField("reason", "简要说明为什么选用此操作"),
                new SchemaStringValue("简短理由"));
        fields.put(new SchemaField("clarificationQuestion", "必须澄清时询问用户，否则为空字符串"),
                new SchemaStringValue("无需澄清时返回空字符串"));
    }

    @Override public String name() { return "steering_analysis_result"; }
    @Override public String description() { return "补充输入对当前执行计划的影响"; }
    @Override public Map<SchemaField, SchemaObjectValue> fields() { return fields; }
    @Override public String promptDescription() {
        return "ADD 为默认选择并保留全部原任务；仅在明确改变既有目标时选 UPDATE/REPLACE，明确放弃目标时选 CANCEL；只指定直接受影响的未执行节点；NOOP 仅用于重复输入。";
    }
}
