package com.github.agentdock.core.provider.schema;

/** JSON Schema 字段：只描述字段名、业务功能及是否必填。 */
public record SchemaField(String name, String description, boolean required) {
    public SchemaField(String name, String description) {
        this(name, description, true);
    }
}
