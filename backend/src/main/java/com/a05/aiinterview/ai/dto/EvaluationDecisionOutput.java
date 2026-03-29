package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评估决策 AI 输出。
 * 对外契约只保留：策略编码 + 焦点 + 域路由 + 沉淀 + 检索计划。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecisionOutput {

    private String decisionReason;
    /** CONTINUE / WRAPUP */
    private String interviewAction;
    /** StrategyCode */
    private String finalDecision;
    private String nextFocus;
    private String nextItemType;
    private String nextItemName;
    private String nextProjectPoint;
    private String targetDomainCode;
    private List<CoveredDomain> newCoveredDomains;
    private List<String> newCoveredPoints;
    private List<RetrievalPlan> retrievalPlans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoveredDomain {
        private String domainCode;
        private String domainName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalPlan {
        private String goal;
        private String displayQuery;
        private String queryText;
        private List<String> keywordHints;
        private String difficultyHint;
        private List<String> mustHaveClues;
        private List<String> avoidClues;
    }
}
