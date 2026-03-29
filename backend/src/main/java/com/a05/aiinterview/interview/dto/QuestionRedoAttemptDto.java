package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单题重答结果 DTO。
 */
@Data
@Schema(description = "单题重答结果")
public class QuestionRedoAttemptDto {

    @Schema(description = "重答记录 ID", example = "7001")
    private Long redoAttemptId;

    @Schema(description = "单题重答评估状态：pending / generating / ready / failed", example = "ready")
    private String evaluationStatus;

    @Schema(description = "本次重答内容")
    private String answerText;

    @Schema(description = "评分", example = "88.0")
    private BigDecimal score;

    @Schema(description = "评语")
    private String commentary;

    @Schema(description = "亮点列表")
    private List<String> strengthPoints;

    @Schema(description = "薄弱点列表")
    private List<String> weakPoints;

    @Schema(description = "知识域评估明细")
    private List<EvaluatedDomainDto> evaluatedDomains;

    @Schema(description = "高亮片段")
    private List<HighlightedSegmentDto> highlightedSegments;

    @Schema(description = "定位批注")
    private List<HighlightedAnnotationDto> highlightedAnnotations;

    @Schema(description = "理想答案骨架")
    private List<String> idealAnswerOutline;

    @Schema(description = "参考重写答案")
    private String rewrittenAnswer;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

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

    @Data
    @Schema(description = "定位批注")
    public static class HighlightedAnnotationDto {
        private Integer start;
        private Integer end;
        private String quote;
        private String label;
        private String comment;
    }
}
