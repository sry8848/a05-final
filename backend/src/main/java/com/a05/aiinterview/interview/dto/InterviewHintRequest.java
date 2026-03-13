package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "获取提示请求")
public class InterviewHintRequest {

    @NotNull(message = "questionId 不能为空")
    private Long questionId;
}
