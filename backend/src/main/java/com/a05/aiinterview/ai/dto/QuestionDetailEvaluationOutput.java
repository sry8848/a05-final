package com.a05.aiinterview.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单题详细评估 AI 出参。
 * 该结构会持久化到 attempt.detailEvaluationJson，并直接供详情页消费。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDetailEvaluationOutput {

    private BigDecimal score;
    private String commentary;
    private List<String> strengthPoints;
    private List<String> weakPoints;
    private List<EvaluatedDomain> evaluatedDomains;
    private List<HighlightedSegment> highlightedSegments;
    private List<HighlightedAnnotation> highlightedAnnotations;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightedAnnotation {
        /** 高亮起始下标，基于原回答，0-based，end exclusive */
        private Integer start;
        /** 高亮结束下标，基于原回答，0-based，end exclusive */
        private Integer end;
        /** 原回答中的连续引用 */
        private String quote;
        /** 标签：strength / weakness */
        private String label;
        /** 对片段的评注 */
        private String comment;
    }
}
