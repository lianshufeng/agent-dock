package com.github.agentdock.core.provider.schema;

/** 数值及其返回范围要求。 */
public final class SchemaNumberValue extends SchemaObjectValue {
    public SchemaNumberValue(String valueDescription) {
        super(valueDescription);
    }
    @Override protected String type() { return "number"; }
}
