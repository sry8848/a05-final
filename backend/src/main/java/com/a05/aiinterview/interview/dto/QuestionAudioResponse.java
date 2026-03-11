package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 题目音频查询响应体。
 *
 * <p>用于前端轮询题目播报音频是否可用。当 {@code ready=true} 时，
 * 前端可使用 {@code audioUrl} 拉取音频并播放；否则继续降级为文本展示。
 */
@Data
@Builder
@Schema(description = "题目播报音频查询响应")
public class QuestionAudioResponse {

    @Schema(description = "音频是否已就绪", example = "true")
    private Boolean ready;

    @Schema(description = "音频访问地址（ready=true 时返回）",
            example = "/api/v1/interviews/1/questions/42/audio/file")
    private String audioUrl;
}

