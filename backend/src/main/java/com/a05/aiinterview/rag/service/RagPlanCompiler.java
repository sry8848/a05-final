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
 */
@Component
public class RagPlanCompiler {

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
