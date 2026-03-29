package com.a05.aiinterview.ai.contract;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单条策略定义。
 */
public record StrategyDefinition(
        StrategyCode code,
        String label,
        String description,
        String applicableWhen,
        StrategyMoveType moveType,
        Set<String> allowedCurrentQuestionTypes,
        String targetQuestionType,
        boolean requiresTargetDomain,
        Set<StrategyRequiredContext> requiredContext,
        Set<StrategyLimit> blockingLimits,
        StrategyQuotaPolicy quotaUpdatePolicy,
        boolean wrapup
) {

    public StrategyDefinition {
        allowedCurrentQuestionTypes = Set.copyOf(allowedCurrentQuestionTypes == null
                ? Set.of()
                : new LinkedHashSet<>(allowedCurrentQuestionTypes));
        requiredContext = Set.copyOf(requiredContext == null
                ? Set.of()
                : new LinkedHashSet<>(requiredContext));
        blockingLimits = Set.copyOf(blockingLimits == null
                ? Set.of()
                : new LinkedHashSet<>(blockingLimits));
    }
}
