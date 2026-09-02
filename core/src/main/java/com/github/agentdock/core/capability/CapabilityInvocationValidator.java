package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.CapabilitySchemaField;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 在能力执行前统一校验参数名称、必填约束、类型、枚举和基础范围。 */
public final class CapabilityInvocationValidator {
    public String validate(CapabilityDefinition definition, Map<String, Object> arguments) {
        if (definition == null) return "能力契约不能为空";
        Map<String, Object> values = arguments == null ? Map.of() : arguments;
        return validateFields(definition.effectiveInputContract(), values, true, "能力参数");
    }

    /** 输出允许携带额外诊断字段，但所有已声明字段一旦返回就必须符合类型和范围。 */
    public String validateOutput(CapabilityDefinition definition, Object output) {
        if (definition == null) return "能力契约不能为空";
        Map<String, CapabilitySchemaField> fields = definition.effectiveOutputContract();
        if (fields.isEmpty()) return null;
        if (!(output instanceof Map<?, ?> raw)) return "能力输出必须是对象";
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        raw.forEach((key, value) -> values.put(String.valueOf(key), value));
        return validateFields(fields, values, false, "能力输出");
    }

    private String validateFields(Map<String, CapabilitySchemaField> fields, Map<String, Object> values,
                                  boolean rejectUnknown, String label) {
        if (rejectUnknown) {
            for (String key : values.keySet()) if (!fields.containsKey(key)) return label + "包含未声明字段: " + key;
        }
        for (Map.Entry<String, CapabilitySchemaField> entry : fields.entrySet()) {
            Object value = values.get(entry.getKey());
            CapabilitySchemaField field = entry.getValue();
            if (field.isRequired() && value == null) return label + "缺少必填字段: " + entry.getKey();
            if (value == null) continue;
            String issue = validateValue(entry.getKey(), field, value, label);
            if (issue != null) return issue;
        }
        return null;
    }

    private String validateValue(String name, CapabilitySchemaField field, Object value, String label) {
        String type = normalizeType(field.getType());
        if ("string".equals(type) && !(value instanceof String)) return label + "字段类型错误: " + name;
        if (("integer".equals(type) || "number".equals(type)) && !(value instanceof Number)) return label + "字段类型错误: " + name;
        if ("boolean".equals(type) && !(value instanceof Boolean)) return label + "字段类型错误: " + name;
        if ("array".equals(type) && !(value instanceof List<?>)) return label + "字段类型错误: " + name;
        if ("object".equals(type) && !isObjectValue(value)) return label + "字段类型错误: " + name;
        if (field.getEnumValues() != null && !field.getEnumValues().isEmpty()
                && !field.getEnumValues().contains(String.valueOf(value))) return label + "字段不在允许枚举中: " + name;
        if (value instanceof Number number) {
            if (field.getMinimum() != null && number.doubleValue() < field.getMinimum()) return label + "字段小于最小值: " + name;
            if (field.getMaximum() != null && number.doubleValue() > field.getMaximum()) return label + "字段大于最大值: " + name;
        }
        if (value instanceof String text) {
            if (field.getMinLength() != null && text.length() < field.getMinLength()) return label + "字段长度不足: " + name;
            if (field.getMaxLength() != null && text.length() > field.getMaxLength()) return label + "字段长度超限: " + name;
        }
        return null;
    }

    private boolean isObjectValue(Object value) {
        return value instanceof Map<?, ?> || value != null
                && !(value instanceof String) && !(value instanceof Number)
                && !(value instanceof Boolean) && !(value instanceof List<?>)
                && !value.getClass().isArray() && !value.getClass().isEnum();
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
