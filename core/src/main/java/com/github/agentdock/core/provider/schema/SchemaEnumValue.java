package com.github.agentdock.core.provider.schema;

import java.util.List;
import java.util.Map;

/** 枚举字符串值及其允许范围。 */
public final class SchemaEnumValue extends SchemaObjectValue {
    private final List<String> values;

    public SchemaEnumValue(String valueDescription, String... values) {
        super(valueDescription);
        this.values = List.of(values);
    }

    @Override protected String type() { return "string"; }

    @Override
    protected void appendConstraints(Map<String, Object> schema) {
        schema.put("enum", values);
    }
}
