package com.github.agentdock.core.model;

import lombok.Data;
import java.util.List;

/** 通用能力参数字段契约；不包含任何具体业务字段或工具描述。 */
@Data
public class CapabilitySchemaField {
    private String type;
    private boolean required;
    private List<String> enumValues = List.of();
    private Double minimum;
    private Double maximum;
    private Integer minLength;
    private Integer maxLength;
    private boolean sensitive;

    public CapabilitySchemaField() { }

    public CapabilitySchemaField(String type, boolean required) {
        this.type = type;
        this.required = required;
    }
}
