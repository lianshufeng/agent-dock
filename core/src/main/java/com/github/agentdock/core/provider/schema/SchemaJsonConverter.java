package com.github.agentdock.core.provider.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

import java.util.Map;

/** 将通用字段和值定义转换为 LangChain4j JSON Schema。 */
public final class SchemaJsonConverter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SchemaJsonConverter() {
    }

    public static JsonSchema convert(String name, String description,
                                     Map<SchemaField, SchemaObjectValue> fields) {
        try {
            SchemaObjectValue root = new SchemaObjectValue(description, fields);
            String json = OBJECT_MAPPER.writeValueAsString(root.toSchema(""));
            return JsonSchema.builder().name(name).rootElement(JsonRawSchema.from(json)).build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("结构化输出 Schema 序列化失败", exception);
        }
    }
}
