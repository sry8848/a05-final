package com.a05.aiinterview.speech;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.dto.AsrTokenResponse;
import com.a05.aiinterview.speech.dto.PauseThresholdsResponse;
import com.a05.aiinterview.speech.service.AsrProxyTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 语音 ASR 配置与代理票据接口。
 *
 * <p>提供两个接口：
 * <ul>
 *   <li>{@code GET /asr/token} — 返回后端代理 WebSocket 地址、短时 ticket 与浏览器采样要求</li>
 *   <li>{@code GET /config/asr-pause-thresholds} — 返回各题型停顿判定阈值</li>
 * </ul>
 *
 * <p><b>安全说明</b>：{@code /asr/token} 需要登录，浏览器不再直接拿到阿里云地址和 API Key。
 */
@Slf4j
@Tag(name = "语音 ASR 配置")
@RestController
@RequiredArgsConstructor
public class AsrController {

    private final SpeechProperties speechProperties;
    private final AsrProxyTicketService asrProxyTicketService;

    /**
     * 获取 ASR 后端代理票据。
     *
     * <p>当 {@code speech.asr.enabled=false} 时返回 {@code enabled=false}，
     * 前端应直接降级为文字输入模式。
     *
     * @return 后端代理 WebSocket 地址、ticket、过期时间与音频采样要求
     */
    @Operation(summary = "获取 ASR 后端代理票据（登录后调用）")
    @GetMapping("/asr/token")
    public ApiResponse<AsrTokenResponse> getAsrToken(
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        SpeechProperties.Asr asr = speechProperties.getAsr();
        log.info("颁发 ASR 凭证，provider={}, model={}, ttl={}s", asr.getProvider(), asr.getModel(), asr.getTokenTtlSeconds());

        if (!asr.isEnabled()) {
            log.info("ASR 功能未启用，返回 enabled=false，前端降级为文字输入");
            return ApiResponse.ok(AsrTokenResponse.builder()
                    .enabled(false)
                    .build());
        }

        AsrProxyTicketService.AsrProxyTicket ticket = asrProxyTicketService.issueTicket(userId);
        String wsUrl = buildProxyWsUrl(request, ticket.getTicket());

        return ApiResponse.ok(AsrTokenResponse.builder()
                .enabled(true)
                .wsUrl(wsUrl)
                .ticket(ticket.getTicket())
                .expiresAt(ticket.getExpiresAt())
                .protocolVersion("v1")
                .audio(AsrTokenResponse.AudioConfig.builder()
                        .format("pcm")
                        .sampleRate(16000)
                        .channels(1)
                        .encoding("pcm16le")
                        .build())
                .build());
    }

    /**
     * 获取各题型停顿判定阈值。
     *
     * <p>当前主要由后端代理 ASR 用于生成最终停顿标签；保留该接口便于前端兼容和调试。
     *
     * @return questionType → 阈值（毫秒）映射
     */
    @Operation(summary = "获取各题型停顿判定阈值")
    @GetMapping("/config/asr-pause-thresholds")
    public ApiResponse<PauseThresholdsResponse> getPauseThresholds() {
        return ApiResponse.ok(PauseThresholdsResponse.builder()
                .thresholds(speechProperties.getAsr().getPauseThresholdConfig())
                .build());
    }

    private String buildProxyWsUrl(HttpServletRequest request, String ticket) {
        String scheme = "https".equalsIgnoreCase(request.getScheme()) ? "wss" : "ws";
        String host = request.getServerName();
        int port = request.getServerPort();
        String portPart = (("ws".equals(scheme) && port == 80) || ("wss".equals(scheme) && port == 443))
                ? ""
                : ":" + port;
        return scheme + "://" + host + portPart + request.getContextPath() + "/asr/stream?ticket=" + ticket;
    }
}
