package com.github.agentdock.core.model;

import java.util.List;
import com.github.agentdock.core.type.IntentComplexity;

/** 某个意图适配器识别出的标准意图。 */
import lombok.Data;

@Data
public class IntentCandidate {
    /** 当前分析结果中的意图实例 ID，用于表达依赖关系。 */
    private String id;
    /** 宿主注册的意图编码；不等于能力编码。 */
    private String code;
    /** 兼容旧版字段；新宿主只需使用 code。 */
    private String intentType;
    private String description;
    /** 面向用户展示的无主语执行进度文案，由意图分析模型生成。 */
    private String progressText;
    /** 当前意图完成时应得到的可验证结果。 */
    private String expectedResult;
    /** 识别置信度；小于等于 0 的候选不会进入执行队列。 */
    private double confidence;
    /** 无依赖冲突时优先级越高越先执行。 */
    private int priority;
    /** 多适配器合并前的原始顺序，用于稳定排序。 */
    private int originalOrder;
    /** 必须先成功执行的意图实例 ID。 */
    private List<String> dependsOn;
    /** 决定走直接能力调用还是 Agent Loop。 */
    private IntentComplexity complexity;
    private ContextRequirement contextRequirement;
    /** LLM 为实现该意图选择的能力及其调用参数。 */
    private CapabilityInvocation capabilityInvocation;
    public IntentCandidate() { }
    public IntentCandidate(String id,String code,String description,String expectedResult,double confidence,int priority,int originalOrder,List<String> dependsOn,IntentComplexity complexity,ContextRequirement contextRequirement,CapabilityInvocation capabilityInvocation) {
        this.id=id;this.code=code;this.description=description;this.expectedResult=expectedResult;this.confidence=confidence;this.priority=priority;this.originalOrder=originalOrder;this.dependsOn=dependsOn==null?List.of():List.copyOf(dependsOn);this.complexity=complexity==null?IntentComplexity.SIMPLE:complexity;this.contextRequirement=contextRequirement==null?ContextRequirement.NONE:contextRequirement;this.capabilityInvocation=capabilityInvocation;
    }
}
