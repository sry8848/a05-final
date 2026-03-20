package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 题目生成 AI 调用入参。
 *
 * <p>新模型以“提问目标驱动”为主，包含 role/project/recent/goal/retrieval/constraints 六类上下文。
 * 旧字段当前保留为迁移支架，待 QuestionStream/OpenAI Prompt 全部切完后删除。
 */
@Data
@Builder
public class QuestionGenerationInput {

    /** 面试会话 ID（审计透传） */
    private Long interviewId;

    /** 当前题目 ID（流式出题时通常为空，统一审计字段保留） */
    private Long questionId;

    /** 题目变体 ID（普通出题场景通常为空，统一审计字段保留） */
    private String variantId;

    /** 岗位编码 */
    private String positionCode;

    /** 面试模式：practice / professional */
    private String mode;

    /** 工作年限枚举 */
    private String experienceLevel;

    private RoleContext roleContext;
    private ProjectContext projectContext;
    private RecentContext recentContext;
    private NextQuestionGoal nextQuestionGoal;
    private RetrievalContext retrievalContext;
    private Constraints constraints;

    /** 已问过的题目列表（避免重复出题） */
    private List<AskedQuestion> askedQuestions;

    /** 主考纲摘要（供 AI 参考整体规划） */
    private Map<String, Object> syllabus;

    /** 简历文本摘要（供 AI 出项目相关题） */
    private String resumeTextSummary;

    // ===== 迁移期兼容字段 =====
    private Long nextDomainId;
    private String nextDomainCode;
    private String nextDomainName;
    private String nextQuestionType;
    private String targetDepth;
    private String difficulty;
    private String targetSkill;
    private List<String> expectedPoints;
    private String ragContext;

    @Data
    @Builder
    public static class RoleContext {
        private String roundType;
        private String candidateLevel;
        private List<String> difficultyBand;
        private String style;
    }

    @Data
    @Builder
    public static class ProjectContext {
        private String activeProjectId;
        private String projectName;
        private String currentFocus;
    }

    @Data
    @Builder
    public static class RecentContext {
        private String lastQuestion;
        private String lastAnswerSummary;
        private String recentTurnsSummary;
        private List<String> lastAnswerHighlights;
    }

    @Data
    @Builder
    public static class NextQuestionGoal {
        private String decision;
        private String targetFocus;
        private String targetAngle;
        private String difficultyAdjustment;
        private String questionType;
        private String focusPoint;
        private String nextQuestionGoal;
        private Long nextDomainId;
        private String nextDomainCode;
        private String nextDomainName;
    }

    @Data
    @Builder
    public static class RetrievalContext {
        private String query;
        private String ragContext;
        private String domainHint;
        private String questionTypeHint;
        private List<String> avoidRecentFamilies;
    }

    @Data
    @Builder
    public static class Constraints {
        private List<String> avoidRepetitionFamilies;
        private boolean mustSoundNatural;
        private Integer maxSentences;
    }

    /** 已问题目摘要（避免重复） */
    @Data
    @Builder
    public static class AskedQuestion {
        private Long questionId;
        private String questionType;
        private String domainCode;
        private String stem;
        private String focusPoint;
        private String questionFamilyId;
        private String activeProjectId;
    }
}
