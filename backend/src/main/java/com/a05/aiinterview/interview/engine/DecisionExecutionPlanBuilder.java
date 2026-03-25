package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCatalog;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DecisionExecutionPlanBuilder {

    private static final Set<String> SAME_DOMAIN_PRINCIPLE_STRATEGIES = Set.of(
            StrategyCode.S_P_VERIFY.code(),
            StrategyCode.S_P_DEEP_LINK.code(),
            StrategyCode.S_P_VARIANT.code(),
            StrategyCode.S_P_SAME_DOMAIN_SHIFT.code()
    );

    public DecisionValidationResult build(InterviewQuestion currentQuestion,
                                          EvaluationDecisionOutput output,
                                          List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains,
                                          List<String> availableStrategies,
                                          DecisionExecutionPlan.EffectiveDecisionSource source) {
        if (output == null) {
            return DecisionValidationResult.failure(List.of("INVALID_STRATEGY_CODE"));
        }
        List<String> errors = new ArrayList<>();
        String interviewAction = normalize(output.getInterviewAction());
        String strategyCode = normalize(output.getFinalDecision());
        String nextFocus = trim(output.getNextFocus());
        String nextItemType = trim(output.getNextItemType());
        String nextItemName = trim(output.getNextItemName());
        String nextProjectPoint = trim(output.getNextProjectPoint());
        String targetDomainCode = trim(output.getTargetDomainCode());

        if (!"CONTINUE".equals(interviewAction) && !"WRAPUP".equals(interviewAction)) {
            errors.add("INVALID_STRATEGY_CODE");
            return DecisionValidationResult.failure(errors);
        }

        if ("WRAPUP".equals(interviewAction)) {
            if (!StrategyCode.S_WRAPUP.code().equals(strategyCode)
                    || !nextFocus.isBlank()
                    || !targetDomainCode.isBlank()
                    || (output.getRetrievalPlans() != null && !output.getRetrievalPlans().isEmpty())) {
                return DecisionValidationResult.failure(List.of("WRAPUP_FIELDS_MUST_BE_EMPTY"));
            }
            return DecisionValidationResult.success(DecisionExecutionPlan.builder()
                    .interviewAction("WRAPUP")
                    .strategyCode(StrategyCode.S_WRAPUP.code())
                    .targetQuestionType("")
                    .nextFocus("")
                    .nextItemType("")
                    .nextItemName("")
                    .nextProjectPoint("")
                    .targetDomainCode("")
                    .targetDomainName("")
                    .newCoveredDomains(safeCoveredDomains(output.getNewCoveredDomains()))
                    .newCoveredPoints(safeStrings(output.getNewCoveredPoints()))
                    .retrievalPlans(List.of())
                    .decisionReason(trim(output.getDecisionReason()))
                    .effectiveDecisionSource(source)
                    .terminationSource(DecisionExecutionPlan.TerminationSource.AI_WRAPUP)
                    .terminationReason(DecisionExecutionPlan.TerminationReason.NORMAL_WRAPUP)
                    .build());
        }

        if (!StrategyCatalog.isAllowed(strategyCode)) {
            errors.add("INVALID_STRATEGY_CODE");
        }
        if (!availableStrategies.isEmpty() && !availableStrategies.contains(strategyCode)) {
            errors.add("STRATEGY_NOT_IN_AVAILABLE_POOL");
        }
        if (nextFocus.isBlank()) {
            errors.add("NEXT_FOCUS_REQUIRED");
        }

        String currentType = currentQuestion == null ? "" : normalize(currentQuestion.getQuestionType());
        String targetQuestionType = StrategyCatalog.targetQuestionType(strategyCode);
        String targetDomainName = "";

        if (StrategyCatalog.requiresTargetDomain(strategyCode)) {
            if (targetDomainCode.isBlank()) {
                errors.add("TARGET_DOMAIN_REQUIRED");
            } else {
                EvaluationDecisionInput.RemainingTargetDomain matched = findRemainingDomain(remainingTargetDomains, targetDomainCode);
                if (matched == null) {
                    errors.add("TARGET_DOMAIN_NOT_IN_MENU");
                } else {
                    targetDomainName = trim(matched.getDomainName());
                }
            }
        } else if ("PRINCIPLE".equals(currentType) && SAME_DOMAIN_PRINCIPLE_STRATEGIES.contains(strategyCode)) {
            if (targetDomainCode.isBlank()) {
                Map<String, Object> context = currentQuestion.getGenerationContextJson() != null
                        ? currentQuestion.getGenerationContextJson()
                        : Map.of();
                targetDomainCode = trim(asString(context.get("domainCode")));
                targetDomainName = trim(asString(context.get("domainName")));
                if (targetDomainCode.isBlank()) {
                    errors.add("PRINCIPLE_DOMAIN_INHERITANCE_FAILED");
                }
            }
        } else if (!targetDomainCode.isBlank()) {
            errors.add("TARGET_DOMAIN_MUST_BE_EMPTY");
        }

        if (!errors.isEmpty()) {
            return DecisionValidationResult.failure(errors.stream().distinct().toList());
        }

        return DecisionValidationResult.success(DecisionExecutionPlan.builder()
                .interviewAction("CONTINUE")
                .strategyCode(strategyCode)
                .targetQuestionType(targetQuestionType)
                .nextFocus(nextFocus)
                .nextItemType(nextItemType)
                .nextItemName(nextItemName)
                .nextProjectPoint(nextProjectPoint)
                .targetDomainCode(targetDomainCode)
                .targetDomainName(targetDomainName)
                .newCoveredDomains(safeCoveredDomains(output.getNewCoveredDomains()))
                .newCoveredPoints(safeStrings(output.getNewCoveredPoints()))
                .retrievalPlans(output.getRetrievalPlans() == null ? List.of() : List.copyOf(output.getRetrievalPlans()))
                .decisionReason(trim(output.getDecisionReason()))
                .effectiveDecisionSource(source)
                .build());
    }

    public static boolean isSameDomainPrincipleStrategy(String strategyCode) {
        return SAME_DOMAIN_PRINCIPLE_STRATEGIES.contains(normalize(strategyCode));
    }

    private EvaluationDecisionInput.RemainingTargetDomain findRemainingDomain(
            List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains,
            String targetDomainCode) {
        if (remainingTargetDomains == null) {
            return null;
        }
        for (EvaluationDecisionInput.RemainingTargetDomain domain : remainingTargetDomains) {
            if (domain != null && normalize(targetDomainCode).equals(normalize(domain.getDomainCode()))) {
                return domain;
            }
        }
        return null;
    }

    private static List<EvaluationDecisionOutput.CoveredDomain> safeCoveredDomains(List<EvaluationDecisionOutput.CoveredDomain> domains) {
        return domains == null ? List.of() : List.copyOf(domains);
    }

    private static List<String> safeStrings(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return trim(value).toUpperCase(Locale.ROOT);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
