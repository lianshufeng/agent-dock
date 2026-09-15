package com.github.agentdock.core.provider;

import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.CapabilitySchemaField;
import com.github.agentdock.core.provider.schema.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 根据已选择能力的输入契约生成精确参数 Schema。 */
public final class CapabilityArgumentsContract implements StructuredOutputContract {
    private final CapabilityDefinition definition;
    private final Map<SchemaField, SchemaObjectValue> fields;

    public CapabilityArgumentsContract(CapabilityDefinition definition) {
        this.definition = definition;
        this.fields = new LinkedHashMap<>();
        definition.effectiveInputContract().forEach((name, field) -> fields.put(
                new SchemaField(name, "能力参数 " + name, field.isRequired()), value(field)));
    }

    @Override public String name() { return "capability_arguments"; }
    @Override public String description() { return "能力 " + definition.getCode() + " 的调用参数"; }
    @Override public Map<SchemaField, SchemaObjectValue> fields() { return fields; }
    @Override public String promptDescription() { return "只返回所选能力声明的参数，不得增加、改写或装饰参数名。"; }

    private SchemaObjectValue value(CapabilitySchemaField field) {
        List<String> enums = field.getEnumValues() == null ? List.of() : field.getEnumValues();
        if (!enums.isEmpty()) return new SchemaEnumValue("只能返回声明的枚举值", enums.toArray(String[]::new));
        String type = field.getType() == null ? "string" : field.getType().trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("integer") || type.startsWith("int")) return new SchemaIntValue("整数");
        if (type.startsWith("number") || type.startsWith("double") || type.startsWith("float"))
            return new SchemaNumberValue("数值");
        if (type.startsWith("boolean") || type.startsWith("bool")) return new SchemaBooleanValue("布尔值");
        if (type.startsWith("array") || type.startsWith("list"))
            return new SchemaArrayValue("数组", new SchemaObjectValue("数组元素", Map.of(), true));
        if (type.startsWith("object") || type.startsWith("map"))
            return new SchemaObjectValue("对象", Map.of(), true);
        return new SchemaStringValue("字符串");
    }
}
