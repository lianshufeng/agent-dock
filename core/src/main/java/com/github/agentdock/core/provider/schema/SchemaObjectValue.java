package com.github.agentdock.core.provider.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema 值定义基类；默认表示对象，子类分别表示具体值类型。 */
public class SchemaObjectValue {
    private final String valueDescription;
    private final Map<SchemaField, SchemaObjectValue> fields;
    private final boolean additionalProperties;

    public SchemaObjectValue(Map<SchemaField, SchemaObjectValue> fields) {
        this(null, fields, false);
    }

    public SchemaObjectValue(String valueDescription, Map<SchemaField, SchemaObjectValue> fields) {
        this(valueDescription, fields, false);
    }

    public SchemaObjectValue(String valueDescription, Map<SchemaField, SchemaObjectValue> fields,
                             boolean additionalProperties) {
        this.valueDescription = valueDescription;
        this.fields = fields == null ? Map.of() : new LinkedHashMap<>(fields);
        this.additionalProperties = additionalProperties;
    }

    protected SchemaObjectValue(String valueDescription) {
        this(valueDescription, Map.of(), false);
    }

    public Map<String, Object> toSchema(String fieldDescription) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type());
        String description = description(fieldDescription);
        if (!description.isBlank()) schema.put("description", description);
        appendConstraints(schema);
        return schema;
    }

    protected String type() {
        return "object";
    }

    protected void appendConstraints(Map<String, Object> schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        fields.forEach((field, value) -> {
            properties.put(field.name(), value.toSchema(field.description()));
            if (field.required()) required.add(field.name());
        });
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        schema.put("additionalProperties", additionalProperties);
    }

    protected final String valueDescription() {
        return valueDescription;
    }

    private String description(String fieldDescription) {
        String field = fieldDescription == null ? "" : fieldDescription.trim();
        String value = valueDescription == null ? "" : valueDescription.trim();
        if (field.isEmpty()) return value;
        if (value.isEmpty()) return field;
        return field + "；返回要求：" + value;
    }
}
