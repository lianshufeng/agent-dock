package com.github.agentdock.core.provider.schema;

/** 字符串值及其返回格式要求。 */
public final class SchemaStringValue extends SchemaObjectValue {
    public SchemaStringValue(String valueDescription) {
        super(valueDescription);
    }
    @Override protected String type() { return "string"; }
}
