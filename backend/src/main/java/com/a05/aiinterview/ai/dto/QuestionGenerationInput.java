package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 题目生成 AI 调用入参。
 * 每次出一道题时传入，包含当前考纲、状态账本和历史问题列表。
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

    /** 本题目标知识域 ID */
    private Long nextDomainId;

    /** 本题目标知识域编码 */
    private String nextDomainCode;

    /** 本题目标知识域中文名 */
    private String nextDomainName;

    /** 本题类型枚举值，如 PRINCIPLE */
    private String nextQuestionType;

    /** 本题目标深度，如 L3 */
    private String targetDepth;

    /** 本题难度等级：L1~L5 */
    private String difficulty;

    /** 本题核心考察点 */
    private String targetSkill;

    /** 本题理想回答要点列表 */
    private List<String> expectedPoints;

    /** 已问过的题目列表（避免重复出题） */
    private List<AskedQuestion> askedQuestions;

    /** 主考纲摘要（供 AI 参考整体规划） */
    private Map<String, Object> syllabus;

    /** 简历文本摘要（供 AI 出项目相关题） */
    private String resumeTextSummary;

    /**
     * RAG 检索结果拼装的知识上下文，注入到出题 Prompt。
     * 为空时正常出题，不影响质量；后续接入 VectorStore 后由 AnswerSubmitService 填充。
     */
    private String ragContext;

    /** 已问题目摘要（避免重复） */
    @Data
    @Builder
    public static class AskedQuestion {
        private Long questionId;
        private String questionType;
        private String domainCode;
        private String stem;
    }
}
