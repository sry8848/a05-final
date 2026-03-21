package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评估决策 AI 输出。
 * 新契约区分机器动作（interviewAction）与自然语言策略描述（finalDecision）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecisionOutput {

    /** CONTINUE / WRAPUP */
    private String interviewAction;
    private String answerSummary;
    private String answerAssessment;
    private String decisionReason;
    private List<String> candidateStrategies;
    private String finalDecision;
    /** THEORY / PROJECT / SCENARIO / SOFT_SKILL */
    private String nextQuestionType;
    private String nextFocus;
    private List<String> expectedAnswerPoints;
    private List<String> possibleNextMoves;
    private List<CoveredDomain> newCoveredDomains;
    private List<String> newCoveredPoints;
    private List<CandidatePointsByDomain> newCandidatePointsByDomain;
    private List<RetrievalPlan> retrievalPlans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoveredDomain {
        private Long domainId;
        private String domainName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidatePointsByDomain {
        private Long domainId;
        private String domainName;
        private List<String> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalPlan {
        private Boolean retrievalNeed;
        private String retrievalGoal;
        private String primaryQuery;
        private List<String> alternateQueries;
        /** questions / domain */
        private String retrievalType;
        private List<String> expectedEvidence;
        private List<String> avoidEvidence;
    }
}
