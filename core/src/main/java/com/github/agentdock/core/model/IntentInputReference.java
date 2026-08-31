package com.github.agentdock.core.model;

import lombok.Data;

/** 后续意图对前置意图输出的显式引用。 */
@Data
public class IntentInputReference {
    private String intentId;
    /** 相对于 IntentResult 根对象的 RFC 6901 JSON Pointer，例如 /output/items/0/id。 */
    private String path;
    private String argumentName;
    private String name;
    public IntentInputReference() { }
    public IntentInputReference(String intentId, String path) { this.intentId = intentId; this.path = path; }
}
