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

/**
 * AI决策执行计划构建器，负责验证和构建标准化的决策执行计划。
 *
 * <p>核心职责：
 * <ol>
 *   <li>验证 AI 决策输出的合法性（策略编码、必填字段、知识域等）</li>
 *   <li>将原始 AI 输出标准化为 DecisionExecutionPlan 执行计划</li>
 *   <li>处理各种边界情况（如知识域继承、策略约束等）</li>
 *   <li>返回验证结果，包含通过或失败原因（错误码列表）</li>
 * </ol>
 *
 * <p>这是容错机制的第一级，确保 AI 决策符合系统约束后才能继续执行
 */
@Component
public class DecisionExecutionPlanBuilder {

    /** 同知识域原则题策略集合（S_P_VERIFY、S_P_DEEP_LINK等） */
    private static final Set<String> SAME_DOMAIN_PRINCIPLE_STRATEGIES = Set.of(
            StrategyCode.S_P_VERIFY.code(),
            StrategyCode.S_P_DEEP_LINK.code(),
            StrategyCode.S_P_VARIANT.code(),
            StrategyCode.S_P_SAME_DOMAIN_SHIFT.code()
    );

    /**
     * 构建并验证决策执行计划。
     *
     * <p>验证流程：
     * <ol>
     *   <li>基本格式校验（interviewAction 必须是 CONTINUE 或 WRAPUP）</li>
     *   <li>如果是 WRAPUP：确保其他字段为空，设置终止源和原因</li>
     *   <li>如果是 CONTINUE：校验策略编码、必填字段、知识域约束等</li>
     *   <li>根据策略类型确定目标题型（PRINCIPLE/PROJECT_DEEP_DIVE等）</li>
     *   <li>处理知识域逻辑（要求、继承、禁止）</li>
     *   <li>如果有错误，返回失败；否则返回成功的执行计划</li>
     * </ol>
     *
     * @param currentQuestion 当前题目
     * @param output AI 原始输出
     * @param remainingTargetDomains 剩余待考察知识域列表
     * @param availableStrategies 可用策略列表
     * @param source 决策来源（RAW_AI/REPAIRED/FALLBACK）
     * @return 验证结果，包含执行计划或错误列表
     */
    public DecisionValidationResult build(InterviewQuestion currentQuestion,
                                          EvaluationDecisionOutput output,
                                          List<EvaluationDecisionInput.RemainingTargetDomain> remainingTargetDomains,
                                          List<String> availableStrategies,
                                          DecisionExecutionPlan.EffectiveDecisionSource source) {
        // 输出为空直接失败
        if (output == null) {
            return DecisionValidationResult.failure(List.of("INVALID_STRATEGY_CODE"));
        }

        List<String> errors = new ArrayList<>();
        // 标准化并提取字段
        String interviewAction = normalize(output.getInterviewAction());
        String strategyCode = normalize(output.getFinalDecision());
        String nextFocus = trim(output.getNextFocus());
        String nextItemType = trim(output.getNextItemType());
        String nextItemName = trim(output.getNextItemName());
        String nextProjectPoint = trim(output.getNextProjectPoint());
        String targetDomainCode = trim(output.getTargetDomainCode());

        // ========== 阶段1：基础校验 ==========
        if (!"CONTINUE".equals(interviewAction) && !"WRAPUP".equals(interviewAction)) {
            errors.add("INVALID_STRATEGY_CODE");
            return DecisionValidationResult.failure(errors);
        }

        // ========== 分支1：WRAPUP 结束面试 ==========
        if ("WRAPUP".equals(interviewAction)) {
            // 校验：WRAPUP 策略必须是 S_WRAPUP，且其他字段必须为空
            if (!StrategyCode.S_WRAPUP.code().equals(strategyCode)
                    || !nextFocus.isBlank()
                    || !targetDomainCode.isBlank()
                    || (output.getRetrievalPlans() != null && !output.getRetrievalPlans().isEmpty())) {
                return DecisionValidationResult.failure(List.of("WRAPUP_FIELDS_MUST_BE_EMPTY"));
            }
            // 返回 WRAPUP 执行计划
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

        // ========== 分支2：CONTINUE 继续面试 ==========
        // 校验策略编码合法性
        if (!StrategyCatalog.isAllowed(strategyCode)) {
            errors.add("INVALID_STRATEGY_CODE");
        }
        // 校验策略在可用策略池内
        if (!availableStrategies.isEmpty() && !availableStrategies.contains(strategyCode)) {
            errors.add("STRATEGY_NOT_IN_AVAILABLE_POOL");
        }
        // 校验必填字段：nextFocus
        if (nextFocus.isBlank()) {
            errors.add("NEXT_FOCUS_REQUIRED");
        }

        String currentType = currentQuestion == null ? "" : normalize(currentQuestion.getQuestionType());
        String targetQuestionType = StrategyCatalog.targetQuestionType(strategyCode);
        String targetDomainName = "";

        // ========== 知识域校验逻辑 ==========
        if (StrategyCatalog.requiresTargetDomain(strategyCode)) {
            // 情况A：策略要求必须有目标知识域
            if (targetDomainCode.isBlank()) {
                errors.add("TARGET_DOMAIN_REQUIRED");
            } else {
                // 验证知识域在剩余待考察列表中
                EvaluationDecisionInput.RemainingTargetDomain matched = findRemainingDomain(remainingTargetDomains, targetDomainCode);
                if (matched == null) {
                    errors.add("TARGET_DOMAIN_NOT_IN_MENU");
                } else {
                    targetDomainName = trim(matched.getDomainName());
                }
            }
        } else if ("PRINCIPLE".equals(currentType) && SAME_DOMAIN_PRINCIPLE_STRATEGIES.contains(strategyCode)) {
            // 情况B：同知识域原则题策略，从上一题继承知识域
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
            // 情况C：策略不需要知识域，但AI提供了 - 报错
            errors.add("TARGET_DOMAIN_MUST_BE_EMPTY");
        }

        // 如果有错误，去重后返回失败
        if (!errors.isEmpty()) {
            return DecisionValidationResult.failure(errors.stream().distinct().toList());
        }

        // ========== 返回成功的执行计划 ==========
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

    /**
     * 判断策略是否属于同知识域原则题策略。
     *
     * @param strategyCode 策略编码
     * @return 是否为同知识域原则题策略
     */
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
