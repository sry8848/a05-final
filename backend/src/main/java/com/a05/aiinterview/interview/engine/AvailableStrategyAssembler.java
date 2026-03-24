package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCatalog;
import com.a05.aiinterview.ai.contract.StrategyDefinition;
import com.a05.aiinterview.ai.contract.StrategyLimit;
import com.a05.aiinterview.ai.contract.StrategyRequiredContext;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拼装 evaluation_decision 的合法策略池。
 */
@Component
public class AvailableStrategyAssembler {

    public List<EvaluationDecisionInput.AvailableStrategy> assemble(String currentQuestionType,
                                                                    boolean hasProjectContext,
                                                                    List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains,
                                                                    Map<String, Object> quotaState) {
        String normalizedType = StrategyCatalog.normalizeQuestionType(currentQuestionType);
        Map<String, Object> normalizedQuotaState = quotaState == null
                ? QuotaStateSupport.initialQuotaState()
                : quotaState;
        Set<String> remainingDomainCodes = extractRemainingDomainCodes(remainingTargetDomains);

        List<StrategyDefinition> pool = new ArrayList<>();
        pool.addAll(StrategyCatalog.internalStrategiesFor(normalizedType));
        pool.addAll(StrategyCatalog.enterStrategies().stream()
                .filter(definition -> !normalizedType.equals(definition.targetQuestionType()))
                .toList());
        pool.add(StrategyCatalog.wrapup());

        return pool.stream()
                .filter(definition -> isAvailable(definition, hasProjectContext, remainingDomainCodes, normalizedQuotaState))
                .map(this::toInput)
                .toList();
    }

    private EvaluationDecisionInput.AvailableStrategy toInput(StrategyDefinition definition) {
        return EvaluationDecisionInput.AvailableStrategy.builder()
                .strategyCode(definition.code().code())
                .label(definition.label())
                .description(definition.description())
                .applicableWhen(definition.applicableWhen())
                .moveType(definition.moveType().name())
                .requiresTargetDomain(definition.requiresTargetDomain())
                .build();
    }

    private boolean isAvailable(StrategyDefinition definition,
                                boolean hasProjectContext,
                                Set<String> remainingDomainCodes,
                                Map<String, Object> quotaState) {
        if (definition.wrapup()) {
            return true;
        }
        if (definition.requiresTargetDomain() && remainingDomainCodes.isEmpty()) {
            return false;
        }
        if (definition.requiredContext().contains(StrategyRequiredContext.PROJECT_CONTEXT) && !hasProjectContext) {
            return false;
        }
        for (StrategyLimit limit : definition.blockingLimits()) {
            if (QuotaStateSupport.toInt(quotaState.get(limit.ledgerKey())) >= limit.maxCount()) {
                return false;
            }
        }
        return true;
    }

    private Set<String> extractRemainingDomainCodes(List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains) {
        Set<String> codes = new LinkedHashSet<>();
        if (remainingTargetDomains == null) {
            return codes;
        }
        for (EvaluationDecisionInput.RemainingTargetDomain domain : remainingTargetDomains) {
            if (domain == null || domain.getDomainCode() == null || domain.getDomainCode().isBlank()) {
                continue;
            }
            codes.add(domain.getDomainCode().trim());
        }
        return codes;
    }
}
