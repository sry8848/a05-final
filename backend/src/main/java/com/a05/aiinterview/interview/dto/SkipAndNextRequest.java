package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "跳过本题并继续请求")
public class SkipAndNextRequest {

    @NotBlank(message = "attemptId 不能为空")
    private String attemptId;
}
