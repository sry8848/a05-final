package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * SSE 流式出题 error 事件数据体。
 * 流式生成过程中发生不可恢复错误时发出，前端收到后应展示错误提示并提供重试入口。
 */
@Data
@Builder
@Schema(description = "SSE 流式出题 error 事件")
public class SseErrorEvent {

    @Schema(description = "错误码", example = "AI_TIMEOUT")
    private String code;

    @Schema(description = "可读错误描述", example = "题目生成超时，请点击重试")
    private String message;
}
