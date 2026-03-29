package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSE 流式出题 done 事件数据体。
 * 流式生成全部完成、题目已落库后发出，携带权威题目快照供前端后续切题使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSE 流式出题 done 事件")
public class SseDoneEvent {

    @Schema(description = "本次生成标识符，与 start 事件中的 generationId 一致", example = "550e8400-e29b-41d4-a716-446655440000")
    private String generationId;

    @Schema(description = "已落库的题目 ID，前端提交回答时使用", example = "42")
    private Long questionId;

    @Schema(description = "已落库的题目快照，前端应以该对象为准")
    private QuestionDto question;

    @Schema(description = "本次流式输出的 token 片段总数（近似值）", example = "85")
    private Integer totalTokens;

    @Schema(description = "是否已触发 TTS 播报任务（false 表示本轮禁用或触发失败）", example = "true")
    private Boolean ttsReady;

    @Schema(description = "题目音频查询接口（ttsReady=true 时返回）",
            example = "/api/v1/interviews/1/questions/42/audio")
    private String audioStatusUrl;
}
