package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 单题重答提交请求。
 */
@Data
@Schema(description = "单题重答提交请求")
public class QuestionRedoAttemptRequest {

    @NotBlank(message = "answerText is required")
    @Schema(description = "重答内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String answerText;
}
