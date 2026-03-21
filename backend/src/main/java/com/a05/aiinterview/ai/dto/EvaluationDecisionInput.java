package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评估决策 AI 调用入参。
 * 使用结构化契约向 prompt 提供候选人上下文、考纲状态、限额状态与本场历史。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationDecisionInput {

    /** 面试会话 ID（审计透传） */
    private Long interviewId;

    /** 当前题目 ID（审计透传） */
    private Long currentQuestionId;

    private InterviewMeta interview;
    private List<ProjectAndInternshipItem> projectAndInternshipSummary;
    private InterviewGoalSummary interviewGoalSummary;
    private List<String> coveredKnowledgeSummary;
    private QuotaSummary quotaSummary;
    private CurrentQuestionContext currentQuestion;
    private String answerText;
    private List<String> expectedPoints;
    private List<String> possibleFutureDirections;
    private List<RetrievedMaterial> retrievedMaterials;
    private List<RecentInterviewMemoryItem> recentInterviewMemory;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewMeta {
        private String positionCode;
        private String experienceLevel;
        private String roundType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectAndInternshipItem {
        private String itemType;
        private String itemName;
        private String resumeDescription;
        private List<String> techHooks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewGoalSummary {
        private List<GoalDomainItem> domains;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalDomainItem {
        private Long domainId;
        private String domainCode;
        private String domainName;
        private List<String> focusPoints;
        /** 仅暴露给 prompt 的状态：UNASKED / COVERED */
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaSummary {
        private LimitCounter samePointContinue;
        private LimitCounter sameDomainContinue;
        private LimitCounter sameProjectPointContinue;
        private LimitCounter sameProjectContinue;
        private LimitCounter sameTypeTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitCounter {
        private Integer count;
        private Integer maxCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentQuestionContext {
        private String stem;
        private String questionType;
        private Long domainId;
        private String domainName;
        private String currentFocus;
        private String relatedItemKey;
        private String relatedItemType;
        private String relatedItemName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedMaterial {
        private String retrievalType;
        private String retrievalGoal;
        private String query;
        private List<String> materials;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentInterviewMemoryItem {
        private Integer questionNo;
        private String questionType;
        private Long domainId;
        private String domainName;
        private String focusPoint;
        private String relatedItemKey;
        private String relatedItemType;
        private String relatedItemName;
        private String questionStem;
        private String answerSummary;
        private String answerAssessment;
    }
}
