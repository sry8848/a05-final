package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单题详细评估 AI 入参。
 * 仅包含题后复盘所需最小上下文，不复用全场报告入参。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDetailEvaluationInput {

    /** 会话 ID（审计透传） */
    private Long interviewId;

    /** 当前题目 ID（审计透传） */
    private Long questionId;

    /** 题目变体 ID（统一审计字段，当前可为空） */
    private String variantId;

    private String positionCode;
    private String experienceLevel;
    private String mode;

    private String questionStem;
    private String questionType;
    private String domainCode;
    private String domainName;

    private String answerText;
    private List<String> expectedPoints;

    /** 近题上下文（可为空） */
    private List<QaContext> recentContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QaContext {
        private String stem;
        private String answer;
        private String questionType;
        private String domainCode;
    }
}
