package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

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
 *   <li>PRINCIPLE/SCENARIO/BEHAVIORAL/PROJECT_DEEP_DIVE：只有有 retrievalPlans 才检索</li>
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
                    .projectName(resolveProjectName(questionType, plan))
                    .queryText("")
                    .denseQueryText("")
                    .sparseQueryText("")
                    .difficultyHint("")
                    .keywordQueries(List.of())
                    .build();
        }

        String queryText = retrievalPlan == null ? "" : defaultString(retrievalPlan.getQueryText());
        List<String> keywordQueries = resolveKeywordQueries(retrievalPlan);
        String difficultyHint = retrievalPlan == null ? "" : defaultString(retrievalPlan.getDifficultyHint());
        return RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText(queryText)
                .denseQueryText(queryText)
                .sparseQueryText(joinKeywords(keywordQueries))
                .keywordQueries(keywordQueries)
                .difficultyHint(difficultyHint)
                .positionCode(defaultString(positionCode))
                .questionType(questionType)
                .experienceLevel(defaultString(experienceLevel))
                .projectName(resolveProjectName(questionType, plan))
                .focusPoint(plan == null ? "" : defaultString(plan.getNextFocus()))
                .build();
    }

    /**
     * 判断是否需要执行RAG检索。
     *
     * <p>检索条件判断：
     * <ul>
     *   <li>PRINCIPLE/SCENARIO/BEHAVIORAL/PROJECT_DEEP_DIVE 题型：只有有 retrievalPlans 才检索</li>
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
        if ("PRINCIPLE".equals(questionType)
                || "SCENARIO".equals(questionType)
                || "BEHAVIORAL".equals(questionType)
                || "PROJECT_DEEP_DIVE".equals(questionType)) {
            return retrievalPlan != null;
        }
        return false;
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

    private String joinKeywords(List<String> keywordQueries) {
        if (keywordQueries == null || keywordQueries.isEmpty()) {
            return "";
        }
        return String.join(" ", keywordQueries);
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
}
