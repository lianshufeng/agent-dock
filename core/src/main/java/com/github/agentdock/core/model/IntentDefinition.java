package com.github.agentdock.core.model;

import lombok.Data;

/** 注册给多意图引擎的意图声明，只描述用户想做什么，不描述工具参数。 */
@Data
public class IntentDefinition {
    /** 全局唯一的通用意图编码，不得与具体能力编码绑定。 */
    private String code;
    /** 供 LLM 判断是否命中该意图的功能描述。 */
    private String description;
    public IntentDefinition() {
    }

    public IntentDefinition(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
