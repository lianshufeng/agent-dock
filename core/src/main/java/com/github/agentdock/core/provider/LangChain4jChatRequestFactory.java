package com.github.agentdock.core.provider;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import com.github.agentdock.core.provider.schema.SchemaJsonConverter;

import java.util.ArrayList;
import java.util.List;

/** LangChain4j 请求构造工具：统一 JSON 输出约束，并支持文本+图片输入。 */
public final class LangChain4jChatRequestFactory {
    private LangChain4jChatRequestFactory() {
    }

    public static ChatRequest jsonUserRequest(String text, List<String> imageUrls, StructuredOutputContract contract) {
        List<Content> contents = userContents(text, imageUrls);
        return ChatRequest.builder()
                .messages(UserMessage.from(contents))
                .responseFormat(dev.langchain4j.model.chat.request.ResponseFormat.builder()
                        .type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON)
                        .jsonSchema(SchemaJsonConverter.convert(
                                contract.name(), contract.description(), contract.fields())).build())
                .build();
    }

    /** 构造不带结构化输出约束的文本+图片用户消息。 */
    public static UserMessage multimodalUserMessage(String text, List<String> imageUrls) {
        return UserMessage.from(userContents(text, imageUrls));
    }

    private static List<Content> userContents(String text, List<String> imageUrls) {
        List<Content> contents = new ArrayList<>();
        contents.add(dev.langchain4j.data.message.TextContent.from(text == null ? "" : text));
        if (imageUrls != null) {
            imageUrls.stream().filter(url -> url != null && !url.isBlank())
                    .map(ImageContent::from).forEach(contents::add);
        }
        return contents;
    }
}
