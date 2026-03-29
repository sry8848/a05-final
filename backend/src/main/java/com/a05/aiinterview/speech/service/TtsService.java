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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String LEGACY_TEXT_TO_AUDIO_PATH = "/api/v1/services/aigc/text2audio/text-to-audio";
    private static final String OFFICIAL_SPEECH_SYNTHESIS_PATH = "/api/v1/services/aigc/text2speech/speech-synthesis";
    private static final String OFFICIAL_WS_INFERENCE_PATH = "/api-ws/v1/inference";

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
     * 调用阿里云 DashScope CosyVoice TTS WebSocket API。
     *
     * <p>官方协议要求使用 WebSocket inference 端点，按 run-task -> continue-task -> finish-task
     * 的顺序下发指令，音频数据通过二进制帧流回传。
     */
    private byte[] requestAlibabaCosyVoice(String text) throws IOException, InterruptedException {
        SpeechProperties.Tts tts = speechProperties.getTts();
        String resolvedEndpoint = resolveTtsWebSocketEndpoint(tts.getEndpoint());

        log.info("========== TTS 请求开始 ==========");
        log.info("[TTS-DEBUG] 配置检查:");
        log.info("[TTS-DEBUG]   - enabled: {}", tts.isEnabled());
        log.info("[TTS-DEBUG]   - endpoint: {}", resolvedEndpoint);
        log.info("[TTS-DEBUG]   - model: {}", tts.getModel());
        log.info("[TTS-DEBUG]   - voice: {}", tts.getVoice());
        log.info("[TTS-DEBUG]   - timeoutMs: {}", tts.getTimeoutMs());
        log.info("[TTS-DEBUG]   - apiKey 长度: {}", tts.getApiKey() != null ? tts.getApiKey().length() : "null");
        log.info("[TTS-DEBUG]   - apiKey 前缀: {}", tts.getApiKey() != null && tts.getApiKey().length() > 8
                ? tts.getApiKey().substring(0, 8) + "..." : "invalid");
        log.info("[TTS-DEBUG]   - 待合成文本长度: {} 字符", text != null ? text.length() : 0);
        log.info("[TTS-DEBUG]   - 待合成文本预览: {}",
                text != null && text.length() > 100 ? text.substring(0, 100) + "..." : text);

        // 验证必要配置
        if (tts.getApiKey() == null || tts.getApiKey().isBlank()) {
            log.error("[TTS-DEBUG] API Key 未配置！");
            throw new IllegalStateException("TTS API Key 未配置，请优先检查 AI_BAILIAN_API_KEY，或确认兼容层 OPENAI_API_KEY 是否已设置");
        }
        if (resolvedEndpoint == null || resolvedEndpoint.isBlank()) {
            log.error("[TTS-DEBUG] Endpoint 未配置！");
            throw new IllegalStateException("TTS Endpoint 未配置");
        }
        if (!resolvedEndpoint.startsWith("ws://") && !resolvedEndpoint.startsWith("wss://")) {
            throw new IllegalStateException("TTS Endpoint 必须是 WebSocket inference 地址，当前值: " + resolvedEndpoint);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(tts.getTimeoutMs(), 3000)))
                .build();
        long startTime = System.currentTimeMillis();
        String taskId = UUID.randomUUID().toString();
        CosyVoiceWebSocketListener listener = new CosyVoiceWebSocketListener(this, taskId, text);

        log.info("[TTS-DEBUG] 请求 URL: {}", resolvedEndpoint);
        log.info("[TTS-DEBUG] run-task JSON: {}", buildRunTaskPayload(taskId));
        log.info("[TTS-DEBUG] continue-task JSON: {}", buildContinueTaskPayload(taskId, text));
        log.info("[TTS-DEBUG] finish-task JSON: {}", buildFinishTaskPayload(taskId));
        log.info("[TTS-DEBUG] 建立 WebSocket 连接...");

        try {
            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(tts.getTimeoutMs(), 3000)))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tts.getApiKey())
                    .buildAsync(URI.create(resolvedEndpoint), listener)
                    .join();

            listener.await(Math.max(tts.getTimeoutMs(), 3000L));
        } catch (CompletionException e) {
            Throwable cause = e.getCause();

            if (cause instanceof IOException ioException) {
                log.error("[TTS-DEBUG] WebSocket 连接 IO 异常: {}", ioException.getMessage());
                throw ioException;
            }

            if (cause instanceof UncheckedIOException uncheckedIOException) {
                log.error("[TTS-DEBUG] WebSocket 连接 IO 异常: {}", uncheckedIOException.getCause().getMessage());
                throw uncheckedIOException.getCause();
            }

            throw new IllegalStateException("建立 TTS WebSocket 连接失败: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[TTS-DEBUG] WebSocket 总耗时: {} ms", elapsed);

        byte[] audioData = listener.audioBytes();
        String requestId = listener.requestId();
        if (requestId != null && !requestId.isBlank()) {
            log.info("[TTS-DEBUG] 请求ID: {}", requestId);
        }

        if (audioData == null || audioData.length == 0) {
            log.error("[TTS-DEBUG] 返回音频数据为空！");
            throw new IllegalStateException("阿里云 TTS 返回空音频数据");
        }

        // 检查音频数据是否为有效 MP3（MP3 文件以 ID3 或 0xFF 开头）
        if (audioData.length > 2) {
            int firstByte = audioData[0] & 0xFF;
            int secondByte = audioData[1] & 0xFF;
            boolean isMp3 = (firstByte == 0xFF && (secondByte & 0xE0) == 0xE0) || // MP3 frame sync
                           (firstByte == 'I' && secondByte == 'D'); // ID3 tag
            log.info("[TTS-DEBUG] 音频格式检测: {} (首字节: 0x{}, 次字节: 0x{})",
                    isMp3 ? "MP3" : "未知格式",
                    String.format("%02X", firstByte),
                    String.format("%02X", secondByte));
        }

        log.info("========== TTS 合成成功 ==========");
        log.info("[TTS-DEBUG] 音频大小: {} bytes ({} KB)", audioData.length, audioData.length / 1024);
        return audioData;
    }

    String resolveTtsWebSocketEndpoint(String configuredEndpoint) {
        if (configuredEndpoint == null || configuredEndpoint.isBlank()) {
            return configuredEndpoint;
        }
        if (configuredEndpoint.startsWith("ws://") || configuredEndpoint.startsWith("wss://")) {
            return configuredEndpoint;
        }
        URI uri = URI.create(configuredEndpoint.contains(LEGACY_TEXT_TO_AUDIO_PATH)
                ? configuredEndpoint.replace(LEGACY_TEXT_TO_AUDIO_PATH, OFFICIAL_SPEECH_SYNTHESIS_PATH)
                : configuredEndpoint);
        String scheme = "https".equalsIgnoreCase(uri.getScheme()) ? "wss"
                : "http".equalsIgnoreCase(uri.getScheme()) ? "ws" : uri.getScheme();
        URI normalized = URI.create("%s://%s%s".formatted(
                scheme,
                authorityOf(uri),
                OFFICIAL_WS_INFERENCE_PATH));
        log.warn("检测到 HTTP TTS endpoint，已自动切换到官方 WebSocket inference 路径: {} -> {}",
                configuredEndpoint, normalized);
        return normalized.toString();
    }

    String buildRunTaskPayload(String taskId) {
        SpeechProperties.Tts tts = speechProperties.getTts();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", Map.of(
                "action", "run-task",
                "task_id", taskId,
                "streaming", "duplex"
        ));
        message.put("payload", Map.of(
                "task_group", "audio",
                "task", "tts",
                "function", "SpeechSynthesizer",
                "model", tts.getModel(),
                "parameters", buildProtocolParameters(tts),
                "input", Map.of()
        ));
        return toProtocolJson(message);
    }

    String buildContinueTaskPayload(String taskId, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", Map.of(
                "action", "continue-task",
                "task_id", taskId,
                "streaming", "duplex"
        ));
        message.put("payload", Map.of(
                "input", Map.of("text", text)
        ));
        return toProtocolJson(message);
    }

    String buildFinishTaskPayload(String taskId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", Map.of(
                "action", "finish-task",
                "task_id", taskId,
                "streaming", "duplex"
        ));
        message.put("payload", Map.of(
                "input", Map.of()
        ));
        return toProtocolJson(message);
    }

    private String toProtocolJson(Map<String, Object> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 TTS WebSocket 指令失败", e);
        }
    }

    private Map<String, Object> buildProtocolParameters(SpeechProperties.Tts tts) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("text_type", "PlainText");
        parameters.put("voice", tts.getVoice());
        parameters.put("format", "mp3");
        parameters.put("sample_rate", 22050);
        parameters.put("volume", 50);
        parameters.put("rate", 1.0);
        parameters.put("pitch", 1.0);
        return parameters;
    }

    private String authorityOf(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getHost() + ":" + uri.getPort();
        }
        return uri.getHost();
    }

    private static final class CosyVoiceWebSocketListener implements WebSocket.Listener {

        private final TtsService ttsService;
        private final String taskId;
        private final String text;
        private final StringBuilder textFrameBuffer = new StringBuilder();
        private final List<byte[]> audioChunks = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicReference<String> requestId = new AtomicReference<>();

        private CosyVoiceWebSocketListener(TtsService ttsService, String taskId, String text) {
            this.ttsService = ttsService;
            this.taskId = taskId;
            this.text = text;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            webSocket.sendText(ttsService.buildRunTaskPayload(taskId), true)
                    .exceptionally(error -> {
                        fail(error);
                        return null;
                    });
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textFrameBuffer.append(data);
            if (last) {
                String message = textFrameBuffer.toString();
                textFrameBuffer.setLength(0);
                handleTextMessage(webSocket, message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            audioChunks.add(chunk);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(error);
        }

        private void handleTextMessage(WebSocket webSocket, String message) {
            try {
                JsonNode root = ttsService.objectMapper.readTree(message);
                JsonNode header = root.path("header");
                String event = header.path("event").asText("");
                String requestUuid = header.path("attributes").path("request_uuid").asText("");
                if (!requestUuid.isBlank()) {
                    requestId.set(requestUuid);
                }
                if ("task-started".equals(event)) {
                    webSocket.sendText(ttsService.buildContinueTaskPayload(taskId, text), true)
                            .thenCompose(ws -> ws.sendText(ttsService.buildFinishTaskPayload(taskId), true))
                            .exceptionally(error -> {
                                fail(error);
                                return null;
                            });
                    return;
                }
                if ("task-finished".equals(event)) {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                    completed.countDown();
                    return;
                }
                if ("task-failed".equals(event)) {
                    String code = header.path("error_code").asText("");
                    String messageText = header.path("error_message").asText(message);
                    fail(new IllegalStateException("DashScope WebSocket TTS 失败: " + code + " " + messageText));
                }
            } catch (Exception e) {
                fail(e);
            }
        }

        private void fail(Throwable error) {
            failure.compareAndSet(null, error);
            completed.countDown();
        }

        private void await(long timeoutMs) throws InterruptedException {
            boolean finished = completed.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new IllegalStateException("TTS WebSocket 调用超时");
            }
            Throwable throwable = failure.get();
            if (throwable == null) {
                return;
            }
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("TTS WebSocket 调用失败: " + throwable.getMessage(), throwable);
        }

        private byte[] audioBytes() {
            int totalBytes = audioChunks.stream().mapToInt(chunk -> chunk.length).sum();
            byte[] merged = new byte[totalBytes];
            int offset = 0;
            for (byte[] chunk : audioChunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.length);
                offset += chunk.length;
            }
            return merged;
        }

        private String requestId() {
            return requestId.get();
        }
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
