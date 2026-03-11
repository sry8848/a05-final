package com.a05.aiinterview.speech;

import com.a05.aiinterview.common.ApiResponse;
import com.a05.aiinterview.speech.config.SpeechProperties;
import com.a05.aiinterview.speech.dto.AsrTokenResponse;
import com.a05.aiinterview.speech.dto.PauseThresholdsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 语音 ASR 配置与凭证接口。
 *
 * <p>提供两个公开接口：
 * <ul>
 *   <li>{@code GET /asr/token} — 返回前端直连 DashScope ASR 所需的 WebSocket URL 和过期时间</li>
 *   <li>{@code GET /config/asr-pause-thresholds} — 返回各题型停顿判定阈值，前端缓存后本地计算</li>
 * </ul>
 *
 * <p><b>安全说明</b>：{@code /asr/token} 已在 SecurityConfig 中配置为需要登录，
 * 避免 API Key 被匿名调用者获取。{@code /config/asr-pause-thresholds} 为公开配置数据。
 */
@Slf4j
@Tag(name = "语音 ASR 配置")
@RestController
@RequiredArgsConstructor
public class AsrController {

    private final SpeechProperties speechProperties;

    /**
     * 获取 ASR 前端直连凭证。
     *
     * <p>将 API Key 编码进 WebSocket URL 的 {@code Authorization} 查询参数，
     * 前端无需手动设置请求头即可直连 DashScope。凭证有效期由配置项
     * {@code speech.asr.token-ttl-seconds} 控制（默认 15 分钟）。
     *
     * <p>当 {@code speech.asr.enabled=false} 时，{@code enabled=false}，
     * 前端收到后应直接降级为文字输入模式。
     *
     * @return ASR 凭证（wsUrl + expiresAt）
     */
    @Operation(summary = "获取 ASR 前端直连凭证（登录后调用）")
    @GetMapping("/asr/token")
    public ApiResponse<AsrTokenResponse> getAsrToken() {
        SpeechProperties.Asr asr = speechProperties.getAsr();
        log.info("颁发 ASR 凭证，provider={}, model={}, ttl={}s", asr.getProvider(), asr.getModel(), asr.getTokenTtlSeconds());

        if (!asr.isEnabled()) {
            log.info("ASR 功能未启用，返回 enabled=false，前端降级为文字输入");
            return ApiResponse.ok(AsrTokenResponse.builder()
                    .enabled(false)
                    .build());
        }

        // 将 Bearer token 编码进 URL 查询参数，绕过浏览器 WebSocket 不支持自定义 Header 的限制
        String encodedAuth = URLEncoder.encode("Bearer " + asr.getApiKey(), StandardCharsets.UTF_8);
        String wsUrl = asr.getEndpoint() + "?model=" + asr.getModel() + "&Authorization=" + encodedAuth;
        long expiresAt = Instant.now().getEpochSecond() + asr.getTokenTtlSeconds();

        return ApiResponse.ok(AsrTokenResponse.builder()
                .enabled(true)
                .wsUrl(wsUrl)
                .model(asr.getModel())
                .expiresAt(expiresAt)
                .build());
    }

    /**
     * 获取各题型停顿判定阈值。
     *
     * <p>前端在初始化时调用，将结果缓存至本地。后续根据当前题目的 {@code questionType}
     * 取对应阈值，检测到超过阈值的静音段时插入 {@code [停顿 Xs]} 标签。
     *
     * @return questionType → 阈值（毫秒）映射
     */
    @Operation(summary = "获取各题型停顿判定阈值（公开接口，前端初始化时拉取）")
    @GetMapping("/config/asr-pause-thresholds")
    public ApiResponse<PauseThresholdsResponse> getPauseThresholds() {
        return ApiResponse.ok(PauseThresholdsResponse.builder()
                .thresholds(speechProperties.getAsr().getPauseThresholdConfig())
                .build());
    }
}
