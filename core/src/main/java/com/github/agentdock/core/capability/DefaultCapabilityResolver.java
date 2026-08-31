package com.github.agentdock.core.capability;

import com.github.agentdock.core.model.CapabilityDefinition;
import com.github.agentdock.core.model.AiExecutionContext;
import com.github.agentdock.core.model.IntentCandidate;

import java.util.List;

/** 默认仅应用宿主可见性策略，不建立意图与工具的静态绑定。 */
public final class DefaultCapabilityResolver implements CapabilityResolver {
    private final CapabilityVisibilityPolicy visibilityPolicy;

    public DefaultCapabilityResolver() {
        this(new AllowAllCapabilityVisibilityPolicy());
    }

    public DefaultCapabilityResolver(CapabilityVisibilityPolicy visibilityPolicy) {
        this.visibilityPolicy = visibilityPolicy == null ? new AllowAllCapabilityVisibilityPolicy() : visibilityPolicy;
    }

    @Override
    public List<CapabilityDefinition> resolve(IntentCandidate intent, AiExecutionContext context, CapabilityRegistry registry) {
        if (intent == null || registry == null) return List.of();
        return registry.definitions().stream()
                .filter(definition -> visibilityPolicy.isVisible(intent, definition, context))
                .toList();
    }
}
