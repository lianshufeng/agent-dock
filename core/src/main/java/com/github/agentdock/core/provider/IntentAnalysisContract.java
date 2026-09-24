package com.github.agentdock.core.provider;

import com.github.agentdock.core.provider.schema.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 意图分析结构化输出契约。 */
public final class IntentAnalysisContract implements StructuredOutputContract {
    public static final IntentAnalysisContract INSTANCE = new IntentAnalysisContract();

    private final Map<SchemaField, SchemaObjectValue> fields = new LinkedHashMap<>() {{
        put(new SchemaField("candidates", "按用户输入识别出的全部意图"),
                new SchemaArrayValue("数组中每项表示一个独立意图",
                        new SchemaObjectValue(new LinkedHashMap<>() {{
                            put(new SchemaField("id", "本次分析中的意图实例标识"),
                                    new SchemaStringValue("本次分析结果内必须唯一"));
                            put(new SchemaField("code", "宿主意图编码"),
                                    new SchemaStringValue("必须取自当前提供的意图目录"));
                            put(new SchemaField("description", "本次具体意图说明"),
                                    new SchemaStringValue("结合本次用户输入生成"));
                            put(new SchemaField("progressText", "面向用户展示的执行进度文案"),
                                    new SchemaStringValue("无主语、拟人化、简短描述准备做什么；禁止返回意图编码和工具名称"));
                            put(new SchemaField("expectedResult", "当前意图的预期结果"),
                                    new SchemaStringValue("描述意图完成时应得到的明确、可判断结果"));
                            put(new SchemaField("confidence", "置信度"),
                                    new SchemaNumberValue("取值范围为 0 到 1"));
                            put(new SchemaField("priority", "执行优先级"),
                                    new SchemaIntValue("非负整数，数值越大越优先"));
                            put(new SchemaField("complexity", "意图复杂度"),
                                    new SchemaEnumValue("只能返回指定枚举值", "SIMPLE", "COMPLEX"));
                            put(new SchemaField("dependsOn", "当前意图依赖的前置意图"),
                                    new SchemaArrayValue("按依赖顺序返回前置意图实例 ID",
                                            new SchemaStringValue("必须引用本次分析结果中的意图实例 ID")));
                            put(new SchemaField("contextRequirement", "当前意图需要的上下文"),
                                    contextRequirementValue());
                        }})));
        put(new SchemaField("deferredBranches", "仅当后续目标取决于前置结果时填写；分支目标暂不放入 candidates"),
                new SchemaArrayValue("互斥条件分支", new SchemaObjectValue(new LinkedHashMap<>() {{
                    put(new SchemaField("id", "分支组唯一 ID"), new SchemaStringValue("本次分析内唯一"));
                    put(new SchemaField("triggerIntentId", "先执行的意图 ID"), new SchemaStringValue("必须引用 candidates 中的意图"));
                    put(new SchemaField("choices", "互斥的候选目标"), new SchemaArrayValue("至少两个选择",
                            new SchemaObjectValue(new LinkedHashMap<>() {{
                                put(new SchemaField("id", "选择 ID"), new SchemaStringValue("组内唯一"));
                                put(new SchemaField("condition", "基于前置真实结果判断的条件"), new SchemaStringValue("保留用户原始条件"));
                                put(new SchemaField("goal", "条件成立后才执行的用户目标"), new SchemaStringValue("保留地点和数值"));
                            }})));
                }})));
        put(new SchemaField("contextRequirement", "本次分析整体需要的上下文"),
                contextRequirementValue());
        put(new SchemaField("clarificationQuestion", "需要用户补充回答的问题"),
                new SchemaStringValue("无需澄清时返回空字符串"));
    }};

    private IntentAnalysisContract() {
    }

    @Override
    public String name() {
        return "intent_analysis_result";
    }

    @Override
    public String description() {
        return "多意图分析结果";
    }

    @Override
    public Map<SchemaField, SchemaObjectValue> fields() {
        return fields;
    }

    @Override
    public String promptDescription() {
        return "只返回当前可执行候选意图、待决条件分支、上下文召回要求和澄清问题，不选择工具，也不生成工具参数。";
    }

    private static SchemaObjectValue contextRequirementValue() {
        return new SchemaObjectValue(new LinkedHashMap<>() {{
            put(new SchemaField("recallHistory", "是否需要召回历史对话"),
                    new SchemaBooleanValue("true 表示需要召回"));
            put(new SchemaField("historyLimit", "需要召回的历史消息数量"),
                    new SchemaIntValue("非负整数，0 表示不召回"));
            put(new SchemaField("scopes", "需要宿主补充的上下文作用域"),
                    new SchemaArrayValue("返回作用域编码列表",
                            new SchemaStringValue("上下文作用域唯一编码")));
        }});
    }
}
