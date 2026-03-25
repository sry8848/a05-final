package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 评估决策 AI 调用入参。
 * 使用结构化契约向 prompt 提供候选人上下文、剩余目标域、策略池与本场历史。
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
    private Integer questionIndex;
    private Integer maxQuestions;
    private Map<String, QuotaSnapshotItem> quotaSnapshot;
    private List<ProjectAndInternshipItem> projectAndInternshipSummary;
    private List<RemainingTargetDomain> remainingTargetDomains;
    private List<String> coveredKnowledgeSummary;
    private List<String> crossSessionBlockedKnowledgePoints;
    private List<AvailableStrategy> availableStrategies;
    private CurrentQuestionContext currentQuestion;
    private String answerText;
    private List<String> expectedPoints;
    private List<RetrievedMaterial> retrievedMaterials;
    private List<RecentInterviewMemoryItem> recentInterviewMemory;
    private Boolean repairMode;
    private Integer repairAttemptNo;
    private String rawDecisionOutput;
    private List<String> validationErrors;

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
    public static class QuotaSnapshotItem {
        private Integer used;
        private Integer max;
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
        private List<String> blockedEntryPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemainingTargetDomain {
        private String domainCode;
        private String domainName;
        private List<String> focusPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableStrategy {
        private String strategyCode;
        private String label;
        private String description;
        private String applicableWhen;
        private String moveType;
        private Boolean requiresTargetDomain;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentQuestionContext {
        private String stem;
        private String questionType;
        private Long domainId;
        private String domainCode;
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
