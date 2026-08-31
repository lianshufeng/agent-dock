package com.github.agentdock.core.provider;

import com.github.agentdock.core.provider.schema.SchemaField;
import com.github.agentdock.core.provider.schema.SchemaObjectValue;
import java.util.Map;

/** 可复用的结构化模型输出契约。 */
public interface StructuredOutputContract {
    String name();
    String description();
    Map<SchemaField, SchemaObjectValue> fields();
    String promptDescription();
}
