package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 将 AI 输出的 retrieval brief 编译为可执行的单库检索请求。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据 AI 决策（DecisionExecutionPlan）决定是否需要检索</li>
 *   <li>如果需要检索，构建完整的 RagRetrievalRequest</li>
 *   <li>如果不需要检索，返回 shouldRetrieve=false 的空请求</li>
 * </ul>
 *
 * <p>检索条件判断：
 * <ul>
 *   <li>PRINCIPLE/SCENARIO/BEHAVIORAL：只有有 retrievalPlans 才检索</li>
 *   <li>PROJECT_DEEP_DIVE：有技术钩子或检索计划才检索</li>
 * </ul>
 *
 * <p>技术钩子（TECH_HOOK_TOKENS）：
 * <ul>
 *   <li>包含特定技术术语（如"redis"、"mysql"、"间隙锁"、"幂等"等）时自动触发检索</li>
 * </ul>
 */
@Component
public class RagPlanCompiler {

    /**
     * 编译决策执行计划为 RAG 检索请求。
     *
     * <p>处理流程：
     * <ol>
     *   <li>标准化题型</li>
     *   <li>提取第一个检索计划</li>
     *   <li>判断是否需要检索（shouldRetrieve）</li>
     *   <li>如果不需要检索：返回 shouldRetrieve=false 的空请求</li>
     *   <li>如果需要检索：构建完整的检索请求</li>
     * </ol>
     *
     * @param plan 决策执行计划（来自AI评估决策）
     * @param positionCode 岗位编码
     * @param experienceLevel 经验级别
     * @return RAG检索请求
     */
    public RagRetrievalRequest compile(DecisionExecutionPlan plan, String positionCode, String experienceLevel) {
        String questionType = normalizeQuestionType(plan == null ? null : plan.getTargetQuestionType());
        EvaluationDecisionOutput.RetrievalPlan retrievalPlan = firstRetrievalPlan(plan);

        boolean shouldRetrieve = shouldRetrieve(questionType, plan, retrievalPlan);
        if (!shouldRetrieve) {
            return RagRetrievalRequest.builder()
                    .shouldRetrieve(false)
                    .questionType(questionType)
                    .focusPoint(plan == null ? "" : defaultString(plan.getNextFocus()))
                    .positionCode(defaultString(positionCode))
                    .experienceLevel(defaultString(experienceLevel))
                    .domainCode(resolveDomainCode(questionType, plan))
                    .projectName(resolveProjectName(questionType, plan))
                    .displayQuery("")
                    .queryText("")
                    .difficultyHint("")
                    .keywordQueries(List.of())
                    .preferredDifficultyLevels(List.of())
                    .mustHaveClues(List.of())
                    .avoidClues(List.of())
                    .build();
        }

        String difficultyHint = retrievalPlan == null ? "" : defaultString(retrievalPlan.getDifficultyHint());
        return RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .displayQuery(retrievalPlan == null ? defaultString(plan.getNextFocus()) : defaultString(retrievalPlan.getDisplayQuery()))
                .queryText(retrievalPlan == null ? defaultString(plan.getNextFocus()) : defaultString(retrievalPlan.getQueryText()))
                .keywordQueries(resolveKeywordQueries(retrievalPlan))
                .difficultyHint(difficultyHint)
                .preferredDifficultyLevels(resolvePreferredDifficultyLevels(difficultyHint))
                .positionCode(defaultString(positionCode))
                .questionType(questionType)
                .experienceLevel(defaultString(experienceLevel))
                .domainCode(resolveDomainCode(questionType, plan))
                .projectName(resolveProjectName(questionType, plan))
                .mustHaveClues(retrievalPlan == null ? List.of() : sanitizeList(retrievalPlan.getMustHaveClues()))
                .avoidClues(retrievalPlan == null ? List.of() : sanitizeList(retrievalPlan.getAvoidClues()))
                .focusPoint(plan == null ? "" : defaultString(plan.getNextFocus()))
                .build();
    }

    /**
     * 判断是否需要执行RAG检索。
     *
     * <p>检索条件判断：
     * <ul>
     *   <li>PRINCIPLE/SCENARIO/BEHAVIORAL 题型：只有有 retrievalPlans 才检索</li>
     *   <li>PROJECT_DEEP_DIVE 题型：有技术钩子或检索计划才检索</li>
     * </ul>
     *
     * <p>技术钩子（TECH_HOOK_TOKENS）判断：
     * <ul>
     *   <li>nextFocus（当前焦点）是否包含技术钩子</li>
     *   <li>nextProjectPoint（项目点）是否包含技术钩子</li>
     *   <li>retrievalPlan.displayQuery（显示查询）是否包含技术钩子</li>
     *   <li>retrievalPlan.queryText（查询文本）是否包含技术钩子</li>
     *   <li>retrievalPlan.keywordHints（关键词提示）是否包含技术钩子</li>
     * </ul>
     *
     * @param questionType 标准化后的题型
     * @param plan 决策执行计划
     * @param retrievalPlan 第一个检索计划
     * @return 是否需要执行检索
     */
    private boolean shouldRetrieve(
            String questionType,
            DecisionExecutionPlan plan,
            EvaluationDecisionOutput.RetrievalPlan retrievalPlan
    ) {
        if ("PRINCIPLE".equals(questionType) || "SCENARIO".equals(questionType) || "BEHAVIORAL".equals(questionType)) {
            return retrievalPlan != null;
        }
        if (!"PROJECT_DEEP_DIVE".equals(questionType)) {
            return false;
        }
        if (hasExplicitProjectTechHook(defaultString(plan == null ? null : plan.getNextFocus()))) {
            return retrievalPlan != null || hasExplicitProjectTechHook(defaultString(plan == null ? null : plan.getNextProjectPoint()));
        }
        if (retrievalPlan == null) {
            return false;
        }
        return hasExplicitProjectTechHook(defaultString(retrievalPlan.getDisplayQuery()))
                || hasExplicitProjectTechHook(defaultString(retrievalPlan.getQueryText()))
                || containsTechnicalHint(retrievalPlan.getKeywordHints());
    }

    /**
     * 提取第一个检索计划。
     *
     * <p>处理逻辑：
     * <ul>
     *   <li>如果 plan 为空，返回 null</li>
     *   <li>如果 retrievalPlans 为空或不存在，返回 null</li>
     *   <li>否则返回第一个检索计划（retrievalPlans.getFirst()）</li>
     * </ul>
     *
     * @param plan 决策执行计划
     * @return 第一个检索计划，或 null
     */
    private EvaluationDecisionOutput.RetrievalPlan firstRetrievalPlan(DecisionExecutionPlan plan) {
        if (plan == null || plan.getRetrievalPlans() == null || plan.getRetrievalPlans().isEmpty()) {
            return null;
        }
        return plan.getRetrievalPlans().getFirst();
    }

    private String resolveDomainCode(String questionType, DecisionExecutionPlan plan) {
        if ("BEHAVIORAL".equals(questionType)) {
            return "";
        }
        return plan == null ? "" : defaultString(plan.getTargetDomainCode());
    }

    private String resolveProjectName(String questionType, DecisionExecutionPlan plan) {
        if (!"PROJECT_DEEP_DIVE".equals(questionType) || plan == null) {
            return "";
        }
        return defaultString(plan.getNextItemName());
    }

    private List<String> resolveKeywordQueries(EvaluationDecisionOutput.RetrievalPlan retrievalPlan) {
        if (retrievalPlan == null) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>(sanitizeList(retrievalPlan.getKeywordHints()));
        return List.copyOf(deduped);
    }

    private List<String> resolvePreferredDifficultyLevels(String difficultyHint) {
        return switch (defaultString(difficultyHint)) {
            case "L1" -> List.of("L1", "L2");
            case "L2" -> List.of("L1", "L2", "L3");
            case "L3" -> List.of("L2", "L3", "L4");
            case "L4" -> List.of("L3", "L4", "L5");
            case "L5" -> List.of("L4", "L5");
            default -> List.of();
        };
    }

    private boolean hasExplicitProjectTechHook(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return TECH_HOOK_TOKENS.stream().anyMatch(token -> normalized.contains(token.toLowerCase(Locale.ROOT)));
    }

    private boolean containsTechnicalHint(List<String> hints) {
        if (hints == null || hints.isEmpty()) {
            return false;
        }
        for (String hint : hints) {
            if (hasExplicitProjectTechHook(hint)) {
                return true;
            }
        }
        return false;
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                deduped.add(value.trim());
            }
        }
        return List.copyOf(deduped);
    }

    private String normalizeQuestionType(String questionType) {
        String normalized = defaultString(questionType).trim().toUpperCase(Locale.ROOT);
        if ("PROJECT".equals(normalized)) {
            return "PROJECT_DEEP_DIVE";
        }
        return normalized;
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private static final Set<String> TECH_HOOK_TOKENS = Set.of(
            "seata",
            "xid",
            "redisson",
            "redis",
            "mysql",
            "mq",
            "rabbitmq",
            "feign",
            "header",
            "ttl",
            "间隙锁",
            "覆盖索引",
            "缓存穿透",
            "看门狗",
            "订单超时关闭",
            "幂等",
            "db+mq",
            "next-key lock"
    );
}
