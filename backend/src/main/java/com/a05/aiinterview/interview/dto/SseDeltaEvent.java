package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * SSE 流式出题 delta 事件数据体。
 * 模型每产出一个 token 片段时发出，前端累积拼接后即为完整题目文本。
 */
@Data
@Builder
@Schema(description = "SSE 流式出题 delta 事件")
public class SseDeltaEvent {

    @Schema(description = "本次增量文本片段", example = "请描述你在高并发")
    private String text;
}
