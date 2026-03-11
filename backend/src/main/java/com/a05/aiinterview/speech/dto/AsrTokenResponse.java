package com.a05.aiinterview.speech.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ASR 前端直连凭证响应。
 *
 * <p>前端在开始语音输入前调用 {@code GET /asr/token} 获取凭证，
 * 使用 {@code wsUrl} 直接建立 DashScope WebSocket 连接。
 * 凭证有效期由 {@code expiresAt}（Unix 秒）标识，前端应在过期前主动刷新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ASR 前端直连凭证")
public class AsrTokenResponse {

    @Schema(description = "ASR 是否已启用", example = "true")
    private boolean enabled;

    @Schema(description = "含鉴权信息的 WebSocket 连接地址（直接使用，无需额外 header）",
            example = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=paraformer-realtime-v2&Authorization=Bearer%20sk-xxx")
    private String wsUrl;

    @Schema(description = "ASR 模型名称", example = "paraformer-realtime-v2")
    private String model;

    @Schema(description = "凭证过期时间（Unix 秒）", example = "1741800000")
    private long expiresAt;
}
