package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * TTS 语音合成服务。
 *
 * <p>职责：
 * <ul>
 *   <li>基于题目文本触发阿里云 CosyVoice 整段合成（M5 一期）</li>
 *   <li>将音频缓存到 Redis，避免重复合成</li>
 *   <li>提供题目维度的音频查询能力，供前端播放器轮询</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private static final String KEY_TEXT_AUDIO = "speech:tts:text:%s";
    private static final String KEY_QUESTION_HASH = "speech:tts:question:%s:%s";
    private static final String KEY_QUESTION_STATE = "speech:tts:state:%s:%s";
    private static final String KEY_ATTEMPT_SEGMENT_HASH = "speech:tts:attempt:%s:%s:segment:%s";
    private static final String KEY_ATTEMPT_SEGMENT_STATE = "speech:tts:attempt:%s:%s:segment:%s:state";

    private final SpeechProperties speechProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 异步触发题目播报音频合成。
     *
     * @param sessionId  面试会话 ID
     * @param questionId 题目 ID
     * @param stemText   题目正文
     * @return true 表示已触发（或已缓存），false 表示 TTS 未启用或参数非法
     */
    public boolean triggerQuestionAudioAsync(Long sessionId, Long questionId, String stemText) {
        SpeechProperties.Tts tts = speechProperties.getTts();
        if (!tts.isEnabled()) {
            return false;
        }
        if (sessionId == null || questionId == null || stemText == null || stemText.isBlank()) {
            log.warn("触发 TTS 失败，参数非法, sessionId={}, questionId={}", sessionId, questionId);
            return false;
        }

        String normalizedText = normalizeText(stemText);
        String textHash = sha256(normalizedText);
        String textAudioKey = String.format(KEY_TEXT_AUDIO, textHash);
        String questionHashKey = questionHashKey(sessionId, questionId);
        String questionStateKey = questionStateKey(sessionId, questionId);
        int ttl = Math.max(tts.getCacheTtlSeconds(), 300);

        redisTemplate.opsForValue().set(questionHashKey, textHash, ttl, TimeUnit.SECONDS);

        String cachedAudio = redisTemplate.opsForValue().get(textAudioKey);
        if (cachedAudio != null && !cachedAudio.isBlank()) {
            redisTemplate.opsForValue().set(questionStateKey, "ready", ttl, TimeUnit.SECONDS);
            log.info("TTS 缓存命中，复用音频, sessionId={}, questionId={}", sessionId, questionId);
            return true;
        }

        // 先标记 processing，再异步合成，避免阻塞主链路。
        redisTemplate.opsForValue().set(questionStateKey, "processing", ttl, TimeUnit.SECONDS);
        CompletableFuture.runAsync(() -> synthesizeAndCache(sessionId, questionId, normalizedText, textHash));
        return true;
    }

    /**
     * 异步触发题目片段音频合成。
     *
     * @param sessionId    面试会话 ID
     * @param attemptId    流式生成 attemptId
     * @param segmentIndex 片段索引（从 0 开始）
     * @param segmentText  片段文本
     * @return Future，完成时 true 表示片段音频已就绪
     */
    public CompletableFuture<Boolean> triggerQuestionSegmentAudioAsync(
            Long sessionId, String attemptId, Integer segmentIndex, String segmentText) {
        SpeechProperties.Tts tts = speechProperties.getTts();
        if (!tts.isEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        if (sessionId == null || attemptId == null || attemptId.isBlank()
                || segmentIndex == null || segmentIndex < 0
                || segmentText == null || segmentText.isBlank()) {
            log.warn("触发片段 TTS 失败，参数非法, sessionId={}, attemptId={}, segmentIndex={}",
                    sessionId, attemptId, segmentIndex);
            return CompletableFuture.completedFuture(false);
        }

        String normalizedText = normalizeText(segmentText);
        if (normalizedText.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String textHash = sha256(normalizedText);
        String textAudioKey = String.format(KEY_TEXT_AUDIO, textHash);
        String segmentHashKey = attemptSegmentHashKey(sessionId, attemptId, segmentIndex);
        String segmentStateKey = attemptSegmentStateKey(sessionId, attemptId, segmentIndex);
        int ttl = Math.max(tts.getCacheTtlSeconds(), 300);

        redisTemplate.opsForValue().set(segmentHashKey, textHash, ttl, TimeUnit.SECONDS);

        String cachedAudio = redisTemplate.opsForValue().get(textAudioKey);
        if (cachedAudio != null && !cachedAudio.isBlank()) {
            redisTemplate.opsForValue().set(segmentStateKey, "ready", ttl, TimeUnit.SECONDS);
            return CompletableFuture.completedFuture(true);
        }

        redisTemplate.opsForValue().set(segmentStateKey, "processing", ttl, TimeUnit.SECONDS);
        return CompletableFuture.supplyAsync(
                () -> synthesizeSegmentAndCache(
                        sessionId, attemptId, segmentIndex, normalizedText, textHash));
    }

    /**
     * 查询题目音频是否已就绪。
     *
     * @param sessionId  面试会话 ID
     * @param questionId 题目 ID
     * @return true 表示音频已缓存可拉取
     */
    public boolean isQuestionAudioReady(Long sessionId, Long questionId) {
        String textHash = redisTemplate.opsForValue().get(questionHashKey(sessionId, questionId));
        if (textHash == null || textHash.isBlank()) {
            return false;
        }
        String audioBase64 = redisTemplate.opsForValue().get(String.format(KEY_TEXT_AUDIO, textHash));
        return audioBase64 != null && !audioBase64.isBlank();
    }

    /**
     * 获取题目音频二进制。
     *
     * @param sessionId  面试会话 ID
     * @param questionId 题目 ID
     * @return 音频字节，不存在时返回 null
     */
    public byte[] getQuestionAudioBytes(Long sessionId, Long questionId) {
        String textHash = redisTemplate.opsForValue().get(questionHashKey(sessionId, questionId));
        if (textHash == null || textHash.isBlank()) {
            return null;
        }
        String audioBase64 = redisTemplate.opsForValue().get(String.format(KEY_TEXT_AUDIO, textHash));
        if (audioBase64 == null || audioBase64.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(audioBase64);
    }

    /**
     * 获取题目片段音频二进制。
     *
     * @param sessionId    面试会话 ID
     * @param attemptId    流式生成 attemptId
     * @param segmentIndex 片段索引
     * @return 音频字节，不存在时返回 null
     */
    public byte[] getAttemptSegmentAudioBytes(Long sessionId, String attemptId, Integer segmentIndex) {
        if (sessionId == null || attemptId == null || attemptId.isBlank()
                || segmentIndex == null || segmentIndex < 0) {
            return null;
        }
        String textHash = redisTemplate.opsForValue().get(
                attemptSegmentHashKey(sessionId, attemptId, segmentIndex));
        if (textHash == null || textHash.isBlank()) {
            return null;
        }
        String audioBase64 = redisTemplate.opsForValue().get(String.format(KEY_TEXT_AUDIO, textHash));
        if (audioBase64 == null || audioBase64.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(audioBase64);
    }

    private void synthesizeAndCache(Long sessionId, Long questionId, String text, String textHash) {
        SpeechProperties.Tts tts = speechProperties.getTts();
        String questionStateKey = questionStateKey(sessionId, questionId);
        String textAudioKey = String.format(KEY_TEXT_AUDIO, textHash);
        int ttl = Math.max(tts.getCacheTtlSeconds(), 300);

        try {
            log.info("TTS 合成开始, sessionId={}, questionId={}, provider={}, model={}",
                    sessionId, questionId, tts.getProvider(), tts.getModel());
            byte[] audio = requestAlibabaCosyVoice(text);
            if (audio == null || audio.length == 0) {
                redisTemplate.opsForValue().set(questionStateKey, "failed", ttl, TimeUnit.SECONDS);
                log.warn("TTS 合成返回空音频, sessionId={}, questionId={}", sessionId, questionId);
                return;
            }
            redisTemplate.opsForValue().set(textAudioKey, Base64.getEncoder().encodeToString(audio), ttl, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(questionStateKey, "ready", ttl, TimeUnit.SECONDS);
            log.info("TTS 合成完成, sessionId={}, questionId={}, size={}", sessionId, questionId, audio.length);
        } catch (Exception e) {
            redisTemplate.opsForValue().set(questionStateKey, "failed", ttl, TimeUnit.SECONDS);
            log.error("题目 TTS 合成失败, 参数: sessionId={}, questionId={}", sessionId, questionId, e);
        }
    }

    private boolean synthesizeSegmentAndCache(
            Long sessionId, String attemptId, Integer segmentIndex, String text, String textHash) {
        SpeechProperties.Tts tts = speechProperties.getTts();
        String segmentStateKey = attemptSegmentStateKey(sessionId, attemptId, segmentIndex);
        String textAudioKey = String.format(KEY_TEXT_AUDIO, textHash);
        int ttl = Math.max(tts.getCacheTtlSeconds(), 300);

        try {
            byte[] audio = requestAlibabaCosyVoice(text);
            if (audio == null || audio.length == 0) {
                redisTemplate.opsForValue().set(segmentStateKey, "failed", ttl, TimeUnit.SECONDS);
                return false;
            }
            redisTemplate.opsForValue().set(textAudioKey, Base64.getEncoder().encodeToString(audio), ttl, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(segmentStateKey, "ready", ttl, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            redisTemplate.opsForValue().set(segmentStateKey, "failed", ttl, TimeUnit.SECONDS);
            log.error("题目片段 TTS 合成失败, sessionId={}, attemptId={}, segmentIndex={}",
                    sessionId, attemptId, segmentIndex, e);
            return false;
        }
    }

    /**
     * 调用阿里云 DashScope CosyVoice TTS API。
     * 
     * <p>HTTP API 端点：POST https://dashscope.aliyuncs.com/api/v1/services/aigc/text2audio/text-to-audio
     * <p>响应格式：直接返回音频二进制数据（MP3/WAV/PCM）
     * 
     * @param text 待合成的文本
     * @return 音频二进制数据
     */
    private byte[] requestAlibabaCosyVoice(String text) throws IOException, InterruptedException {
        SpeechProperties.Tts tts = speechProperties.getTts();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(tts.getTimeoutMs(), 3000)))
                .build();

        // 构建符合阿里云 DashScope API 格式的请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", tts.getModel());
        body.put("input", Map.of("text", text));
        
        // 构建参数，添加必要的音频参数
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("voice", tts.getVoice());
        parameters.put("format", "mp3");
        parameters.put("sample_rate", 22050);
        parameters.put("volume", 50);
        parameters.put("rate", 1.0);
        parameters.put("pitch", 1.0);
        body.put("parameters", parameters);

        String requestBody = objectMapper.writeValueAsString(body);
        log.debug("TTS 请求体: {}", requestBody);
        
        HttpRequest request = HttpRequest.newBuilder(URI.create(tts.getEndpoint()))
                .timeout(Duration.ofMillis(Math.max(tts.getTimeoutMs(), 3000)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tts.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        // HTTP API 直接返回音频二进制，不是 JSON
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        
        if (response.statusCode() >= 400) {
            // 尝试解析错误信息
            String errorBody = response.body() != null ? new String(response.body(), StandardCharsets.UTF_8) : "无响应体";
            log.error("阿里云 TTS 调用失败，status={}, body={}", response.statusCode(), errorBody);
            throw new IllegalStateException("阿里云 TTS 调用失败，status=" + response.statusCode() + ", body=" + errorBody);
        }

        byte[] audioData = response.body();
        if (audioData == null || audioData.length == 0) {
            throw new IllegalStateException("阿里云 TTS 返回空音频数据");
        }
        
        log.debug("TTS 合成成功，音频大小: {} bytes", audioData.length);
        return audioData;
    }

    private String questionHashKey(Long sessionId, Long questionId) {
        return String.format(KEY_QUESTION_HASH, sessionId, questionId);
    }

    private String questionStateKey(Long sessionId, Long questionId) {
        return String.format(KEY_QUESTION_STATE, sessionId, questionId);
    }

    private String attemptSegmentHashKey(Long sessionId, String attemptId, Integer segmentIndex) {
        return String.format(KEY_ATTEMPT_SEGMENT_HASH, sessionId, attemptId, segmentIndex);
    }

    private String attemptSegmentStateKey(Long sessionId, String attemptId, Integer segmentIndex) {
        return String.format(KEY_ATTEMPT_SEGMENT_STATE, sessionId, attemptId, segmentIndex);
    }

    private String normalizeText(String stemText) {
        return stemText.replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算文本哈希失败", e);
        }
    }
}
