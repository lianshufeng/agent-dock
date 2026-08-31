package com.github.agentdock.core.provider.schema;

/** 布尔值及其返回语义要求。 */
public final class SchemaBooleanValue extends SchemaObjectValue {
    public SchemaBooleanValue(String valueDescription) {
        super(valueDescription);
    }
    @Override protected String type() { return "boolean"; }
}
