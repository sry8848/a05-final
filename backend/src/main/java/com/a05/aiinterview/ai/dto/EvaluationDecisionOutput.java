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

    /**
     * 辅助输出：对候选人当前话语的简短理解。
     * 仅用于帮助模型先理解再决策，不参与后端决策分支。
     */
    private String answerUnderstanding;
    /**
     * 辅助输出：对当前主线规划的简短判断。
     * 仅用于帮助模型先规划再映射策略，不参与后端决策分支。
     */
    private String planningIntent;
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
        /** 独立、完整的自然语言语义查询，供 dense/rerank 直接使用。 */
        private String queryText;
        /** sparse/BM25 使用的术语锚点；为空时后端直接跳过 sparse。 */
        private List<String> keywordHints;
        /** 目标难度提示；后端会按配置将其解析为相邻一级 difficulty window 硬过滤。 */
        private String difficultyHint;
    }
}
