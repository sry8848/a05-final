package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 面试偏好 DTO，用于 GET/PUT /interview-preferences/latest 接口的请求和响应体。
 */
@Data
@Schema(description = "面试准备偏好")
public class InterviewPreferenceDto {

    @Schema(description = "最近选择的目标岗位", example = "JAVA_BACKEND")
    private String positionCode;

    @Schema(description = "最近选择的工作年限", example = "SENIOR")
    private String experienceLevel;

    @Schema(description = "最近选择的面试模式", example = "practice")
    private String mode;

    @Schema(description = "最近填写的侧重知识点", example = "Redis 持久化、JVM 调优")
    private String focusTopics;

    @Schema(description = "最近设置的思考时间限制（秒）", example = "30")
    private Integer thinkTimeLimitSeconds;

    @Schema(description = "最近设置的回答时间限制（秒）", example = "180")
    private Integer answerTimeLimitSeconds;
}
