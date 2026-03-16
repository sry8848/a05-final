package com.a05.aiinterview.speech.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ASR 后端代理票据响应。
 *
 * <p>前端在开始语音输入前调用 {@code GET /asr/token} 获取后端代理地址、
 * ticket 与浏览器采样要求。浏览器只连接本系统后端，不再直接连接阿里云。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ASR 前端直连凭证")
public class AsrTokenResponse {

    @Schema(description = "ASR 是否已启用", example = "true")
    private boolean enabled;

    @Schema(description = "后端代理 WebSocket 地址",
            example = "ws://localhost:8080/api/v1/asr/stream?ticket=550e8400-e29b-41d4-a716-446655440000")
    private String wsUrl;

    @Schema(description = "后端代理票据", example = "550e8400-e29b-41d4-a716-446655440000")
    private String ticket;

    @Schema(description = "凭证过期时间（Unix 秒）", example = "1741800000")
    private long expiresAt;

    @Schema(description = "浏览器采样要求")
    private AudioConfig audio;

    @Schema(description = "前后端代理协议版本", example = "v1")
    private String protocolVersion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudioConfig {
        @Schema(description = "音频格式", example = "pcm")
        private String format;
        @Schema(description = "采样率", example = "16000")
        private int sampleRate;
        @Schema(description = "通道数", example = "1")
        private int channels;
        @Schema(description = "编码格式", example = "pcm16le")
        private String encoding;
    }
}
