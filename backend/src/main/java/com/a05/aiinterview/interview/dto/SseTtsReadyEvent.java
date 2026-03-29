package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * SSE 题目片段音频就绪事件。
 */
@Data
@Builder
@Schema(description = "SSE 题目片段音频就绪事件")
public class SseTtsReadyEvent {

    @Schema(description = "本次生成标识符，与 start 事件中的 generationId 一致", example = "550e8400-e29b-41d4-a716-446655440000")
    private String generationId;

    @Schema(description = "片段索引（从 0 开始）", example = "0")
    private Integer segmentIndex;

    @Schema(description = "片段音频访问地址（需携带 Authorization）",
            example = "/api/v1/interviews/1/attempts/550e8400-e29b-41d4-a716-446655440000/audio/segments/0/file")
    private String audioUrl;
}
