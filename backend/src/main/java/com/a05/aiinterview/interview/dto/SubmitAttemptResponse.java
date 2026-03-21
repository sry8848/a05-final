package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 提交回答响应体。
 * 对应 POST /interviews/{sessionId}/attempts 接口的返回值。
 *
 * <p>M2 升级说明：下一题不再由本接口同步生成并返回 stem，而是通过 SSE 流式实时生成。
 * 前端收到响应后，使用 {@code streamAttemptId} 调用 SSE 端点
 * {@code GET /interviews/{sessionId}/questions/stream?attemptId=xxx} 获取实时题目流。
 */
@Data
@Builder
@Schema(description = "提交回答响应")
public class SubmitAttemptResponse {

    @Schema(description = "本次提交的幂等键，与请求中 attemptId 一致", example = "550e8400-e29b-41d4-a716-446655440000")
    private String attemptId;

    @Schema(description = "下一步决策：continue / wrapup", example = "continue")
    private String decision;

    /**
     * SSE 流式出题标识符。
     * decision != wrapup 时与 attemptId 相同，前端使用该值调用
     * {@code GET /interviews/{sessionId}/questions/stream?attemptId={streamAttemptId}}
     * 以获取实时题目流；decision=wrapup 时为 null。
     */
    @Schema(description = "流式出题标识符，用于调用 SSE 端点（decision=wrapup 时为 null）",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private String streamAttemptId;

    /**
     * 当前会话状态。
     * 正常答题时为 in_progress；面试结束时为 report_generating。
     */
    @Schema(description = "当前会话状态", example = "in_progress")
    private String sessionStatus;
}
