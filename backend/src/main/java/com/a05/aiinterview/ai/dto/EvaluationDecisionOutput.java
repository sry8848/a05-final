package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 评估决策 AI 输出。
 *
 * <p>新契约以“提问目标驱动”为主，最小必要结构包括：
 * answerAssessment / answerVerdict / decision / targetFocus / targetAngle /
 * difficultyAdjustment / nextQuestionGoal / questionType / focusPoint /
 * nextDomain* / retrievalIntent / domainOutcome / statePatch。
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecisionOutput {

    private String answerAssessment;
    private String answerVerdict;
    private String decision;
    private String targetFocus;
    private String targetAngle;
    private String difficultyAdjustment;
    private String nextQuestionGoal;
    private Long nextDomainId;
    private String nextDomainCode;
    private String nextDomainName;
    private String questionType;
    private String focusPoint;
    private String domainOutcome;
    private RetrievalIntent retrievalIntent;
    private Map<String, Object> statePatch;
    private Tags tags;

    /** 兼容旧日志字段。 */
    private String reasoning;

    /** 兼容旧日志字段。 */
    private String summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalIntent {
        private String domainHint;
        private String focusQuery;
        private String questionTypeHint;
        private List<String> avoidRecentFamilies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tags {
        private String questionFamilyHint;
        private String interviewerIntent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextQuestionStrategy {

        private Long nextDomainId;
        private String nextDomainCode;
        private String nextDomainName;
        private String questionType;
        private String targetDepth;
        private String targetSkill;
        private List<String> expectedPoints;
        private String difficulty;
        private String focusPoint;
    }
}
