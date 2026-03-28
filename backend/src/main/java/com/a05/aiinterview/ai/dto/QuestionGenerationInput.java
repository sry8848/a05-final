package com.a05.aiinterview.ai.dto;

import com.a05.aiinterview.rag.dto.RagContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 题目生成 AI 调用入参。
 * 只保留新 prompt 真实消费的上下文块，不再保留深度/难度迁移字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionGenerationInput {

    private Long interviewId;
    private Long questionId;
    private String positionCode;
    private String mode;
    private String experienceLevel;

    private RoleContext roleContext;
    private ProjectContext projectContext;
    private RecentContext recentContext;
    private NextQuestionGoal nextQuestionGoal;
    private RetrievalContext retrievalContext;
    private Constraints constraints;

    private List<AskedQuestion> askedQuestions;
    private Map<String, Object> syllabus;
    private String resumeTextSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleContext {
        private String roundType;
        private String style;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectContext {
        private String activeItemKey;
        private String itemType;
        private String itemName;
        private String currentFocus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentContext {
        private String lastQuestion;
        private String lastAnswerSummary;
        private String recentTurnsSummary;
        private List<String> lastAnswerHighlights;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextQuestionGoal {
        private String questionType;
        private String nextFocus;
        private String goalSummary;
        private String relatedDomainCode;
        private String relatedDomainName;
        private String relatedItemKey;
        private String relatedItemType;
        private String relatedItemName;
        private List<String> expectedAnswerPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievalContext {
        private String summary;
        private List<EvaluationDecisionOutput.RetrievalPlan> retrievalPlans;
        private List<RagContext.RetrievedMaterial> retrievedMaterials;
        private List<String> followUpCandidates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Constraints {
        private List<String> avoidRepetitionFamilies;
        private boolean mustSoundNatural;
        private Integer maxSentences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AskedQuestion {
        private Long questionId;
        private String questionType;
        private String domainCode;
        private String stem;
        private String focusPoint;
        private String questionFamilyId;
        private String activeItemKey;
    }
}
