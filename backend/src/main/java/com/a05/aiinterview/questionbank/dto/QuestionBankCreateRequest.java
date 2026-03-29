package com.a05.aiinterview.questionbank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "收藏到问答库请求")
public class QuestionBankCreateRequest {

    @NotNull(message = "questionId 不能为空")
    @Schema(description = "题目 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    @NotNull(message = "sessionId 不能为空")
    @Schema(description = "会话 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sessionId;

    @Schema(description = "自定义标签")
    private String tag;
}
