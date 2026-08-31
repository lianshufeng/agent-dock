package com.github.agentdock.core.provider.schema;

import java.util.Map;

/** 数组值及其元素类型和返回要求。 */
public final class SchemaArrayValue extends SchemaObjectValue {
    private final SchemaObjectValue item;

    public SchemaArrayValue(String valueDescription, SchemaObjectValue item) {
        super(valueDescription);
        this.item = item;
    }

    @Override protected String type() { return "array"; }

    @Override
    protected void appendConstraints(Map<String, Object> schema) {
        schema.put("items", item.toSchema(""));
    }
}
