package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建面试会话响应 DTO。
 * 前端收到后立即跳转面试测试页，并保存 sessionId 用于后续轮询。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建面试会话响应")
public class CreateInterviewResponse {

    @Schema(description = "面试会话 ID", example = "3001")
    private Long sessionId;

    @Schema(description = "当前会话状态，创建后为 planning", example = "planning")
    private String status;
}
