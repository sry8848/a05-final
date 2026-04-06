package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单题详细评估 AI 输出 DTO（模型专用）。
 * 该结构不包含 start/end 定位信息，这些由后端 HighlightedAnnotationLocator 计算。
 * 模型只输出 quote/label/comment，后端负责定位到原文中的字符区间。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDetailEvaluationAiOutput {

    private BigDecimal score;
    private String commentary;
    private List<String> strengthPoints;
    private List<String> weakPoints;
    private List<EvaluatedDomain> evaluatedDomains;
    private List<HighlightedSegment> highlightedSegments;
    private List<HighlightedAnnotationCandidate> highlightedAnnotations;
    private List<String> idealAnswerOutline;
    private String rewrittenAnswer;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluatedDomain {
        private String domainCode;
        private String domainName;
        private BigDecimal score;
        private String commentary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightedSegment {
        /** 高亮文本片段 */
        private String segment;
        /** 标签：strength / weakness */
        private String label;
        /** 对片段的评注 */
        private String comment;
    }

    /**
     * 单题详细评估 AI 输出的高亮注释候选（模型专用）。
     * 不包含 start/end，由后端定位器计算。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightedAnnotationCandidate {
        /** 原回答中的连续引用（模型输出） */
        private String quote;
        /** 标签：strength / weakness */
        private String label;
        /** 对片段的评注 */
        private String comment;
    }
}
