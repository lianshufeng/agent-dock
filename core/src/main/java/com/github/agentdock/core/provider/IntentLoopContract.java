package com.github.agentdock.core.provider;

import com.github.agentdock.core.provider.schema.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单个意图每轮规划的结构化输出契约。 */
public final class IntentLoopContract implements StructuredOutputContract {
    public static final IntentLoopContract INSTANCE = new IntentLoopContract();

    private final Map<SchemaField, SchemaObjectValue> fields = new LinkedHashMap<>() {{
        put(new SchemaField("status", "当前意图本轮执行状态"),
                new SchemaEnumValue("只能返回指定枚举值", "CONTINUE", "COMPLETED", "WAITING_USER", "UNRESOLVABLE"));
        put(new SchemaField("completeAfterTools", "本轮所有工具成功后是否已满足当前意图预期结果"),
                new SchemaBooleanValue("只有本轮全部工具声明 terminalResult 且无需继续分析或调用时才为 true"));
        put(new SchemaField("toolInvocations", "状态为 CONTINUE 时需要执行的工具调用"),
                new SchemaArrayValue("不需要调用工具时返回空数组",
                        new SchemaObjectValue(new LinkedHashMap<>() {{
                            put(new SchemaField("invocationId", "工具调用节点唯一标识；需要被其他调用依赖时必须填写"),
                                    new SchemaStringValue("同一轮内唯一，可为空"));
                            put(new SchemaField("dependsOnInvocationIds", "同一意图内需要先完成的工具调用节点"),
                                    new SchemaArrayValue("无依赖时返回空数组", new SchemaStringValue("引用同轮已声明的 invocationId")));
                            put(new SchemaField("capabilityCode", "能力编码"),
                                    new SchemaStringValue("必须取自当前候选能力目录"));
                            put(new SchemaField("arguments", "能力调用参数"),
                                    new SchemaObjectValue("键和值必须符合对应能力的 inputSchema",
                                            new LinkedHashMap<>(), true));
                            put(new SchemaField("inputRefs", "需要由服务端绑定到参数的前置结果字段"),
                                    new SchemaArrayValue("无引用时返回空数组",
                                            new SchemaObjectValue(new LinkedHashMap<>() {{
                                                put(new SchemaField("intentId", "结果所属意图实例 ID"), new SchemaStringValue("可填写当前意图 ID 引用同一任务内的上一次能力结果，或填写当前意图 dependsOn 中的前置意图 ID"));
                                                put(new SchemaField("path", "结果输出路径"), new SchemaStringValue("从 output 开始，如 output.data.facts.0.value"));
                                                put(new SchemaField("argumentName", "写入的工具参数名"), new SchemaStringValue("必须是 inputSchema 已声明字段"));
                                                put(new SchemaField("name", "写入对象参数时使用的字段名"), new SchemaStringValue("直接写入参数时返回空字符串"));
                                            }})));
                        }})));
        put(new SchemaField("result", "当前意图已经完成时的最终结果", false),
                new SchemaStringValue("状态为 COMPLETED 时返回完整结果，否则可返回空字符串"));
        put(new SchemaField("reason", "无法解决或需要继续执行的原因", false),
                new SchemaStringValue("简要说明决策依据"));
    }};

    private IntentLoopContract() {
    }

    @Override public String name() { return "intent_loop_decision"; }
    @Override public String description() { return "单个意图当前轮次的执行决策"; }
    @Override public Map<SchemaField, SchemaObjectValue> fields() { return fields; }
    @Override public String promptDescription() {
        return "只能从当前候选能力目录中选择已注册且符合输入 Schema 的能力；如果没有能力能够完成当前意图，应返回 UNRESOLVABLE，不得虚构能力或参数。已有信息足以满足预期结果时返回 COMPLETED。";
    }
}
