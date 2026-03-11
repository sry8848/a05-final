package com.a05.aiinterview.interview.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 提交回答请求体。
 * 对应 POST /interviews/{sessionId}/attempts 接口的请求参数。
 *
 * <p>语音模式下，{@code answerText} 为停顿打标后的富文本（含 {@code [停顿 Xs]} 标签），
 * 同时附带 {@code pauseStats} 供 AI 评估表达流畅度；文字模式这两个字段均为 null。
 */
@Data
@Schema(description = "提交回答请求")
public class SubmitAttemptRequest {

    @NotNull(message = "questionId 不能为空")
    @Schema(description = "被回答的题目 ID", example = "5001", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long questionId;

    /**
     * 客户端生成的全局唯一幂等键（建议使用 UUID v4）。
     * 同一个 attemptId 重试时直接返回历史结果，不会重复扣配额或生成下一题。
     */
    @NotBlank(message = "attemptId 不能为空")
    @Schema(description = "客户端生成的唯一幂等键（UUID）", example = "550e8400-e29b-41d4-a716-446655440000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String attemptId;

    @NotBlank(message = "answerText 不能为空")
    @Schema(description = "候选人回答文本（语音模式为含停顿标签的富文本）",
            example = "Redis 缓存击穿 [停顿 3.2s] 可以通过互斥锁或逻辑过期来解决...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String answerText;

    /**
     * 是否为最终版回答。
     * 文字模式默认 true；语音模式应等收到 Final 帧后再提交 true。
     */
    @Schema(description = "是否为最终版回答（文字模式默认 true）", example = "true")
    private Boolean isFinal = true;

    // ── 语音模式附加字段（文字模式均为 null，后端安全忽略）─────────────────────

    /**
     * 语速与停顿统计，由前端 AsrService 计算后上报。
     * <ul>
     *   <li>wpm：每分钟字数（Words Per Minute），用于评估表达流畅度</li>
     *   <li>longPauseCount：超过阈值的停顿次数</li>
     *   <li>longestPauseMs：最长单次停顿时长（毫秒）</li>
     * </ul>
     */
    @Schema(description = "语音停顿统计（语音模式专属）",
            example = "{\"wpm\":142,\"longPauseCount\":2,\"longestPauseMs\":3200}")
    private Map<String, Object> pauseStats;

    /**
     * ASR 识别片段列表（语音模式专属），每条含文本、开始时间、结束时间。
     * 用于审计和未来的精细化停顿分析，当前评估链路不直接消费此字段。
     */
    @Schema(description = "ASR 识别片段列表（语音模式专属）",
            example = "[{\"text\":\"Redis缓存击穿\",\"beginTime\":0,\"endTime\":1200}]")
    private List<Map<String, Object>> asrSegments;

    /**
     * 答题音频 URL（语音模式专属，当前为预留字段）。
     * 若未来支持音频存储，此处传入 OSS URL；当前版本后端不处理此字段。
     */
    @Schema(description = "答题音频 URL（预留字段，当前版本不处理）")
    private String audioUrl;
}
