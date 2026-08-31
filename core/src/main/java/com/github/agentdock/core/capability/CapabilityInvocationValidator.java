package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.CapabilitySchemaField;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 在能力执行前统一校验参数名称、必填约束、类型、枚举和基础范围。 */
public final class CapabilityInvocationValidator {
    public String validate(CapabilityDefinition definition, Map<String, Object> arguments) {
        Map<String, Object> values = arguments == null ? Map.of() : arguments;
        Map<String, CapabilitySchemaField> fields = definition.effectiveInputContract();
        for (String key : values.keySet()) {
            if (!fields.containsKey(key)) return "能力参数包含未声明字段: " + key;
        }
        for (Map.Entry<String, CapabilitySchemaField> entry : fields.entrySet()) {
            Object value = values.get(entry.getKey());
            CapabilitySchemaField field = entry.getValue();
            if (field.isRequired() && value == null) return "能力缺少必填参数: " + entry.getKey();
            if (value == null) continue;
            String type = normalizeType(field.getType());
            if (("integer".equals(type) || "number".equals(type)) && !(value instanceof Number))
                return "能力参数类型错误: " + entry.getKey();
            if ("boolean".equals(type) && !(value instanceof Boolean))
                return "能力参数类型错误: " + entry.getKey();
            if ("array".equals(type) && !(value instanceof List<?>))
                return "能力参数类型错误: " + entry.getKey();
            if ("object".equals(type) && !(value instanceof Map<?, ?>))
                return "能力参数类型错误: " + entry.getKey();
            if (field.getEnumValues() != null && !field.getEnumValues().isEmpty()
                    && !field.getEnumValues().contains(String.valueOf(value)))
                return "能力参数不在允许枚举中: " + entry.getKey();
            if (value instanceof Number number) {
                if (field.getMinimum() != null && number.doubleValue() < field.getMinimum())
                    return "能力参数小于最小值: " + entry.getKey();
                if (field.getMaximum() != null && number.doubleValue() > field.getMaximum())
                    return "能力参数大于最大值: " + entry.getKey();
            }
            if (value instanceof String text) {
                if (field.getMinLength() != null && text.length() < field.getMinLength())
                    return "能力参数长度不足: " + entry.getKey();
                if (field.getMaxLength() != null && text.length() > field.getMaxLength())
                    return "能力参数长度超限: " + entry.getKey();
            }
        }
        return null;
    }

    private String normalizeType(String value) {
        if (value == null) return "string";
        String type = value.trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("enum:")) return "string";
        if (type.startsWith("string")) return "string";
        if (type.startsWith("integer") || type.startsWith("int")) return "integer";
        if (type.startsWith("number")) return "number";
        if (type.startsWith("boolean") || type.startsWith("bool")) return "boolean";
        if (type.startsWith("array")) return "array";
        if (type.startsWith("object")) return "object";
        return type;
    }
}
