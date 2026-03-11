package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * SSE 流式出题 start 事件数据体。
 * 流开始时发出，携带本次生成的唯一标识和会话 ID，供前端初始化状态。
 */
@Data
@Builder
@Schema(description = "SSE 流式出题 start 事件")
public class SseStartEvent {

    @Schema(description = "本次生成标识符（与 attemptId 相同），断线重连时使用", example = "550e8400-e29b-41d4-a716-446655440000")
    private String generationId;

    @Schema(description = "面试会话 ID", example = "1")
    private Long sessionId;
}
