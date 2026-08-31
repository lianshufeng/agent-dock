package com.github.agentdock.core.provider.schema;

/** 整数值及其返回范围要求。 */
public final class SchemaIntValue extends SchemaObjectValue {
    public SchemaIntValue(String valueDescription) {
        super(valueDescription);
    }
    @Override protected String type() { return "integer"; }
}
