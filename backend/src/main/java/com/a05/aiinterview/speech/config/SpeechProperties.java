package com.a05.aiinterview.speech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 语音能力（ASR/TTS）全局配置属性。
 *
 * <p>通过 {@code speech.*} 配置前缀注入。当 {@code speech.asr.enabled=false}（默认）时，
 * ASR 功能完全关闭，前端降级为文字输入模式，与旧行为完全兼容。
 */
@Data
@Component
@ConfigurationProperties(prefix = "speech")
public class SpeechProperties {

    private Asr asr = new Asr();
    private Tts tts = new Tts();

    /**
     * ASR（自动语音识别）配置。
     */
    @Data
    public static class Asr {

        /** 是否启用 ASR，默认 false（向后兼容） */
        private boolean enabled = false;

        /** 云厂商，当前仅支持 alibaba（阿里云百炼） */
        private String provider = "alibaba";

        /** 阿里云百炼 API Key（生产环境通过环境变量 ASR_API_KEY 注入） */
        private String apiKey = "sk-mock";

        /** DashScope Paraformer WebSocket inference 端点 */
        private String endpoint = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

        /** 使用的 ASR 模型，推荐 paraformer-realtime-v2 */
        private String model = "paraformer-realtime-v2";

        /**
         * 前端直连凭证有效期（秒）。
         * 前端在凭证过期前应主动刷新，避免 WSS 连接因鉴权失败而中断。
         */
        private int tokenTtlSeconds = 900;

        /**
         * 各题型停顿判定阈值（毫秒）。
         * key 为 questionType 枚举值（大写），value 为毫秒数。
         * 前端检测到超过阈值的静音段时，在 answerText 中插入 {@code [停顿 Xs]} 标签。
         */
        private Map<String, Integer> pauseThresholdConfig = new HashMap<>(Map.of(
                "INTRO", 2000,
                "PRINCIPLE", 2500,
                "SCENARIO", 3000,
                "PROJECT_DEEP_DIVE", 2000,
                "BEHAVIORAL", 2500
        ));
    }

    /**
     * TTS（文本转语音）配置。
     */
    @Data
    public static class Tts {

        /** 是否启用 TTS，默认 false（向后兼容） */
        private boolean enabled = false;

        /** 云厂商，当前实现为 alibaba（阿里云 CosyVoice） */
        private String provider = "alibaba";

        /** 阿里云百炼 API Key（生产环境通过环境变量 TTS_API_KEY 注入） */
        private String apiKey = "sk-mock";

        /** CosyVoice 文本转语音模型名 */
        private String model = "cosyvoice-v1";

        /** 发音人，建议使用中文主播音色 */
        private String voice = "longxiaochun";

        /** DashScope CosyVoice WebSocket inference 地址 */
        private String endpoint = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

        /** 题目音频缓存 TTL（秒） */
        private int cacheTtlSeconds = 3600;

        /** 拉取远程音频的超时时间（毫秒） */
        private int timeoutMs = 10000;
    }
}
