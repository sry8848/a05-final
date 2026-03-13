package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单题复盘详情 DTO。
 */
@Data
@Schema(description = "单题复盘详情")
public class InterviewQuestionReviewDto {

    @Schema(description = "题目 ID", example = "9001")
    private Long questionId;

    @Schema(description = "题号", example = "1")
    private Integer questionNo;

    @Schema(description = "题干")
    private String questionStem;

    @Schema(description = "题型", example = "PRINCIPLE")
    private String questionType;

    @Schema(description = "知识域名称", example = "Java 并发编程")
    private String domainName;

    @Schema(description = "目标深度", example = "L3")
    private String targetDepth;

    @Schema(description = "用户回答")
    private String userAnswer;

    @Schema(description = "作答状态：answered / skipped / pending", example = "answered")
    private String answerStatus;

    @Schema(description = "详细评估状态：pending / generating / ready / failed", example = "ready")
    private String evaluationStatus;

    @Schema(description = "单题分数（可为空）", example = "82.5")
    private BigDecimal score;

    @Schema(description = "评语（可为空）")
    private String commentary;

    @Schema(description = "亮点列表（可为空）")
    private List<String> strengthPoints;

    @Schema(description = "薄弱点列表（可为空）")
    private List<String> weakPoints;

    @Schema(description = "知识域评估明细（可为空）")
    private List<EvaluatedDomainDto> evaluatedDomains;

    @Schema(description = "回答高亮片段（可为空）")
    private List<HighlightedSegmentDto> highlightedSegments;

    @Schema(description = "理想答案骨架（可为空）")
    private List<String> idealAnswerOutline;

    @Schema(description = "参考重写答案（可为空）")
    private String rewrittenAnswer;

    @Schema(description = "是否允许前端本地兜底补齐", example = "true")
    private Boolean backfillFromLocalAllowed;

    @Data
    @Schema(description = "知识域评估明细")
    public static class EvaluatedDomainDto {
        private String domainCode;
        private String domainName;
        private BigDecimal score;
        private String commentary;
    }

    @Data
    @Schema(description = "高亮片段")
    public static class HighlightedSegmentDto {
        private String segment;
        private String label;
        private String comment;
    }
}
