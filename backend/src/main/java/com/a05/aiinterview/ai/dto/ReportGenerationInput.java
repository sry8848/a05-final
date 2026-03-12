package com.a05.aiinterview.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 报告生成 AI 调用入参。
 * 面试结束后传入，包含全部 Q/A 记录、考纲和状态账本，
 * 供 AI 生成总体评价和逐知识域明细。
 */
@Data
@Builder
public class ReportGenerationInput {

    /** 面试会话 ID（审计透传） */
    private Long interviewId;

    /** 当前题目 ID（报告生成场景通常为空，统一审计字段保留） */
    private Long questionId;

    /** 题目变体 ID（报告生成场景通常为空，统一审计字段保留） */
    private String variantId;

    /** 岗位编码，如 JAVA_BACKEND */
    private String positionCode;

    /** 工作年限枚举，如 SENIOR */
    private String experienceLevel;

    /** 面试模式：practice / professional */
    private String mode;

    /** 本场面试标题 */
    private String sessionTitle;

    /**
     * 主考纲（JSON），包含知识域目标深度和题型规划。
     * 供 AI 对照"规划 vs 实际"给出有针对性的评价。
     */
    private Map<String, Object> syllabusJson;

    /**
     * 最终状态账本（JSON），包含各域覆盖深度、saturated 状态等。
     * 是 AI 判断候选人整体掌握情况的关键信号。
     */
    private Map<String, Object> stateLedgerJson;

    /**
     * 完整 Q/A 配对列表（按题号升序），是报告生成的核心素材。
     */
    private List<QuestionAnswerPair> questionAnswerPairs;

    /**
     * 单题 Q/A 配对，包含题目信息和候选人回答。
     */
    @Data
    @Builder
    public static class QuestionAnswerPair {
        /** 题目 ID */
        private Long questionId;
        /** 题号（从 1 开始） */
        private Integer questionNo;
        /** 题目类型，如 PRINCIPLE */
        private String questionType;
        /** 知识域编码 */
        private String domainCode;
        /** 知识域中文名 */
        private String domainName;
        /** 题目目标深度 */
        private String targetDepth;
        /** 题目正文 */
        private String stem;
        /** 候选人回答（未回答时为 null） */
        private String answerText;
        /** 该题期望回答要点列表（供 AI 对照评分） */
        private List<String> expectedPoints;
    }
}
