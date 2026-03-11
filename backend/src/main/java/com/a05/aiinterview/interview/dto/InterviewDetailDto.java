package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 面试会话详情 DTO，对应 GET /interviews/{sessionId} 的返回值。
 * 加载页通过轮询此接口判断考纲是否准备就绪，status=in_progress 时内嵌首题。
 */
@Data
@Schema(description = "面试会话详情")
public class InterviewDetailDto {

    @Schema(description = "面试会话 ID", example = "3001")
    private Long id;

    @Schema(description = "本场面试标题", example = "Java 后端开发模拟面试 - 2026-03-09")
    private String title;

    @Schema(description = "目标岗位枚举值", example = "JAVA_BACKEND")
    private String targetRole;

    @Schema(description = "工作年限枚举值", example = "SENIOR")
    private String experienceLevel;

    @Schema(description = "面试模式", example = "practice")
    private String mode;

    @Schema(description = "当前题号，0 表示尚未开始", example = "1")
    private Integer currentQuestionNo;

    @Schema(description = "当前会话状态", example = "in_progress")
    private String status;

    @Schema(description = "考纲摘要（status=in_progress 后返回，loading 页展示用）")
    private SyllabusSummaryDto syllabusSummary;

    @Schema(description = "当前题目（仅 status=in_progress 时返回首题）")
    private QuestionDto currentQuestion;
}
