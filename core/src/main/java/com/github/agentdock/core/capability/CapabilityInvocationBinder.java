package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.*;
import com.github.agentdock.core.type.IntentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 在执行前按显式引用将前置意图输出绑定到能力参数。 */
public final class CapabilityInvocationBinder {
    private static final ObjectMapper JSON = new ObjectMapper();
    public CapabilityInvocation bind(IntentCandidate intent, AiExecutionContext context, CapabilityInvocation invocation,
                                     CapabilityDefinition definition) {
        List<IntentInputReference> references = invocation.getInputRefs() == null ? List.of() : invocation.getInputRefs();
        if (references.isEmpty()) return invocation;
        Map<String, Object> arguments = new LinkedHashMap<>(
                invocation.getArguments() == null ? Map.of() : invocation.getArguments());
        for (IntentInputReference reference : references) {
            validate(intent, reference, definition);
            IntentResult source = context.getResults().get(reference.getIntentId());
            // 同一任务内的串行能力调用可引用前一个能力输出。
            if (source == null && intent.getId().equals(reference.getIntentId())) {
                Object previous = context.getCurrentTaskObservation();
                if (previous instanceof IntentResult previousResult) source = previousResult;
            }
            if (source == null || source.getStatus() != IntentStatus.SUCCESS)
                throw new IllegalArgumentException("引用的前置意图尚未成功: " + reference.getIntentId());
            Object value = resolve(source, reference.getPath());
            String target = targetArgument(reference, definition);
            validateValue(target, value, definition);
            if (target.equals("inputs") || reference.getName() != null && !reference.getName().isBlank()) {
                Object current = arguments.get(target);
                Map<String, Object> values = new LinkedHashMap<>();
                if (current instanceof Map<?, ?> map) {
                    map.forEach((key, item) -> { if (key instanceof String name) values.put(name, item); });
                }
                values.put(reference.getName() == null || reference.getName().isBlank()
                        ? reference.getArgumentName() : reference.getName(), value);
                arguments.put(target, values);
            } else {
                arguments.put(target, value);
            }
        }
        CapabilityInvocation bound = new CapabilityInvocation(invocation.getCapabilityCode(), arguments);
        bound.setInputRefs(List.copyOf(references));
        return bound;
    }

    private void validateValue(String argumentName, Object value, CapabilityDefinition definition) {
        String type = definition.effectiveInputContract().get(argumentName) == null
                ? definition.getInputSchema().get(argumentName).toLowerCase(Locale.ROOT)
                : definition.effectiveInputContract().get(argumentName).getType().toLowerCase(Locale.ROOT);
        if ("inputs".equals(argumentName)) return;
        if (("number".equals(type) || "integer".equals(type)) && !(value instanceof Number))
            throw new IllegalArgumentException("前置结果类型与能力参数不匹配: " + argumentName);
        if ("boolean".equals(type) && !(value instanceof Boolean))
            throw new IllegalArgumentException("前置结果类型与能力参数不匹配: " + argumentName);
        if ("array".equals(type) && !(value instanceof List<?>))
            throw new IllegalArgumentException("前置结果类型与能力参数不匹配: " + argumentName);
        if ("object".equals(type) && !(value instanceof Map<?, ?>))
            throw new IllegalArgumentException("前置结果类型与能力参数不匹配: " + argumentName);
    }

    private void validate(IntentCandidate intent, IntentInputReference reference, CapabilityDefinition definition) {
        if (reference == null || reference.getIntentId() == null
                || !(Objects.requireNonNullElse(intent.getDependsOn(), List.<String>of()).contains(reference.getIntentId())
                || intent.getId().equals(reference.getIntentId())))
            throw new IllegalArgumentException("能力输入只能引用当前意图的前置依赖");
        if (reference.getArgumentName() == null || reference.getArgumentName().contains(".")
                || definition.getInputSchema() == null
                || (!definition.getInputSchema().containsKey(reference.getArgumentName())
                && !(definition.getInputSchema().containsKey("inputs")
                && reference.getName() != null && !reference.getName().isBlank())))
            throw new IllegalArgumentException("引用目标不是能力已声明参数");
    }

    private String targetArgument(IntentInputReference reference, CapabilityDefinition definition) {
        return definition.getInputSchema().containsKey(reference.getArgumentName())
                ? reference.getArgumentName() : "inputs";
    }

    private Object resolve(IntentResult result, String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("INVALID_INPUT_REF_PATH: 前置结果引用路径不能为空");
        if (!path.startsWith("/") || path.contains("[") || path.contains("]"))
            throw new IllegalArgumentException("INVALID_INPUT_REF_PATH: 路径必须符合 RFC 6901 JSON Pointer 格式: " + path);
        try {
            JsonNode node = JSON.valueToTree(result).at(path);
            if (node.isMissingNode()) throw new IllegalArgumentException("INPUT_REF_FIELD_NOT_FOUND: " + path);
            if (node.isNull()) throw new IllegalArgumentException("INPUT_REF_VALUE_NULL: " + path);
            return JSON.convertValue(node, Object.class);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("INPUT_REF_")) throw exception;
            throw new IllegalArgumentException("INVALID_INPUT_REF_PATH: " + path, exception);
        }
    }
}
