package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class AliyunAsrWebSocketClient {

    private static final String LEGACY_REALTIME_PATH = "/api-ws/v1/realtime";
    private static final String OFFICIAL_INFERENCE_PATH = "/api-ws/v1/inference";

    private final SpeechProperties speechProperties;
    private final ObjectMapper objectMapper;

    public SessionConnection openSession(Listener listener) throws IOException {
        SpeechProperties.Asr asr = speechProperties.getAsr();
        String endpoint = resolveWebSocketEndpoint(asr.getEndpoint());
        String taskId = UUID.randomUUID().toString();
        AtomicReference<WebSocket> socketRef = new AtomicReference<>();
        AtomicBoolean ready = new AtomicBoolean(false);

        log.info("[ASR-DEBUG] 准备连接阿里云 ASR, endpoint={}, model={}, taskId={}",
                endpoint, asr.getModel(), taskId);
        log.info("[ASR-DEBUG] run-task JSON: {}", buildRunTaskPayload(taskId));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(3000, asr.getTokenTtlSeconds() * 10L)))
                .build();

        WebSocket.Listener wsListener = new WebSocket.Listener() {
            private final StringBuilder textBuffer = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                socketRef.set(webSocket);
                log.info("[ASR-DEBUG] 阿里云 ASR WebSocket 已连接, taskId={}", taskId);
                webSocket.request(1);
                webSocket.sendText(buildRunTaskPayload(taskId), true)
                        .exceptionally(error -> {
                            log.error("[ASR-DEBUG] 发送 run-task 失败, taskId={}", taskId, error);
                            listener.onTaskFailed(taskId, "proxy_send_failed", error.getMessage());
                            return null;
                        });
            }

            @Override
            public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                textBuffer.append(data);
                if (last) {
                    String message = textBuffer.toString();
                    textBuffer.setLength(0);
                    log.info("[ASR-DEBUG] 收到阿里云文本消息, taskId={}, message={}", taskId, message);
                    handleText(taskId, message, listener, ready);
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                log.info("[ASR-DEBUG] 阿里云 ASR WebSocket 关闭, taskId={}, statusCode={}, reason={}",
                        taskId, statusCode, reason);
                listener.onClosed(taskId, statusCode, reason);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.error("[ASR-DEBUG] 阿里云 ASR WebSocket 异常, taskId={}", taskId, error);
                listener.onTaskFailed(taskId, "websocket_closed", error.getMessage());
            }
        };

        try {
            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(5000))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + asr.getApiKey())
                    .buildAsync(URI.create(endpoint), wsListener)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("连接阿里云 ASR 失败: " + cause.getMessage(), cause);
        }

        return new SessionConnection(taskId, socketRef, ready);
    }

    private void handleText(String taskId, String message, Listener listener, AtomicBoolean ready) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode header = root.path("header");
            String event = header.path("event").asText("");
            if ("task-started".equals(event)) {
                log.info("[ASR-DEBUG] task-started, taskId={}", taskId);
                ready.set(true);
                listener.onTaskStarted(taskId);
                return;
            }
            if ("result-generated".equals(event)) {
                JsonNode sentence = root.path("payload").path("output").path("sentence");
                log.info("[ASR-DEBUG] result-generated, taskId={}, text={}, sentenceEnd={}, heartbeat={}, beginTime={}, endTime={}",
                        taskId,
                        sentence.path("text").asText(""),
                        sentence.path("sentence_end").asBoolean(false),
                        sentence.path("heartbeat").asBoolean(false),
                        sentence.path("begin_time").isMissingNode() ? null : sentence.path("begin_time").asInt(),
                        sentence.path("end_time").isNull() || sentence.path("end_time").isMissingNode()
                                ? null : sentence.path("end_time").asInt());
                listener.onSentence(new AsrTranscriptAggregator.SentenceResult(
                        sentence.path("text").asText(""),
                        sentence.path("begin_time").isMissingNode() ? null : sentence.path("begin_time").asInt(),
                        sentence.path("end_time").isNull() || sentence.path("end_time").isMissingNode()
                                ? null : sentence.path("end_time").asInt(),
                        sentence.path("heartbeat").asBoolean(false),
                        sentence.path("sentence_end").asBoolean(false)
                ));
                return;
            }
            if ("task-finished".equals(event)) {
                log.info("[ASR-DEBUG] task-finished, taskId={}", taskId);
                listener.onTaskFinished(taskId);
                return;
            }
            if ("task-failed".equals(event)) {
                log.error("[ASR-DEBUG] task-failed, taskId={}, errorCode={}, errorMessage={}",
                        taskId,
                        header.path("error_code").asText("task-failed"),
                        header.path("error_message").asText(message));
                listener.onTaskFailed(
                        taskId,
                        header.path("error_code").asText("task-failed"),
                        header.path("error_message").asText(message));
            }
        } catch (Exception e) {
            log.error("[ASR-DEBUG] 解析阿里云消息失败, taskId={}, message={}", taskId, message, e);
            listener.onTaskFailed(taskId, "invalid_message", e.getMessage());
        }
    }

    private String resolveWebSocketEndpoint(String configuredEndpoint) {
        if (configuredEndpoint == null || configuredEndpoint.isBlank()) {
            return "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        }
        if (configuredEndpoint.contains(LEGACY_REALTIME_PATH)) {
            return configuredEndpoint.replace(LEGACY_REALTIME_PATH, OFFICIAL_INFERENCE_PATH);
        }
        return configuredEndpoint;
    }

    private String buildRunTaskPayload(String taskId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", Map.of(
                "action", "run-task",
                "task_id", taskId,
                "streaming", "duplex"
        ));
        message.put("payload", Map.of(
                "task_group", "audio",
                "task", "asr",
                "function", "recognition",
                "model", speechProperties.getAsr().getModel(),
                "parameters", buildRunTaskParameters(),
                "input", Map.of()
        ));
        return writeJson(message);
    }

    private Map<String, Object> buildRunTaskParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("format", "pcm");
        parameters.put("sample_rate", 16000);
        parameters.put("language_hints", List.of("zh"));
        parameters.put("punctuation_prediction_enabled", true);
        parameters.put("inverse_text_normalization_enabled", true);
        parameters.put("disfluency_removal_enabled", true);
        parameters.put("multi_threshold_mode_enabled", true);
        parameters.put("max_sentence_silence", 1500);
        parameters.put("semantic_punctuation_enabled", false);
        return parameters;
    }

    private String buildFinishTaskPayload(String taskId) {
        return writeJson(Map.of(
                "header", Map.of(
                        "action", "finish-task",
                        "task_id", taskId,
                        "streaming", "duplex"
                ),
                "payload", Map.of("input", Map.of())
        ));
    }

    private String writeJson(Map<String, Object> message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            throw new IllegalStateException("序列化阿里云 ASR 指令失败", e);
        }
    }

    public interface Listener {
        void onTaskStarted(String taskId);

        void onSentence(AsrTranscriptAggregator.SentenceResult sentence);

        void onTaskFinished(String taskId);

        void onTaskFailed(String taskId, String code, String message);

        void onClosed(String taskId, int statusCode, String reason);
    }

    public final class SessionConnection {
        private final String taskId;
        private final AtomicReference<WebSocket> socketRef;
        private final AtomicBoolean ready;
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private SessionConnection(String taskId, AtomicReference<WebSocket> socketRef, AtomicBoolean ready) {
            this.taskId = taskId;
            this.socketRef = socketRef;
            this.ready = ready;
        }

        public boolean isReady() {
            return ready.get();
        }

        public String taskId() {
            return taskId;
        }

        public void sendAudio(byte[] audioBytes) {
            WebSocket socket = socketRef.get();
            if (socket == null || !ready.get()) {
                return;
            }
            log.debug("[ASR-DEBUG] 转发音频帧到阿里云, taskId={}, bytes={}", taskId, audioBytes.length);
            socket.sendBinary(ByteBuffer.wrap(audioBytes), true);
        }

        public void finish() {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            WebSocket socket = socketRef.get();
            if (socket != null) {
                log.info("[ASR-DEBUG] 发送 finish-task, taskId={}", taskId);
                socket.sendText(buildFinishTaskPayload(taskId), true);
            }
        }

        public void close() {
            WebSocket socket = socketRef.get();
            if (socket != null) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            }
        }
    }
}
