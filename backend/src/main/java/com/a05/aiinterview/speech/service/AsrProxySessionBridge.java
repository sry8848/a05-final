package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class AsrProxySessionBridge {

    private final String sessionId;
    private final AsrProxyTicketService.ClaimedTicket claimedTicket;
    private final Consumer<String> outboundEmitter;
    private final ObjectMapper objectMapper;
    private final SpeechProperties speechProperties;
    private final AsrConstrainedCorrectionService correctionService;
    private final AsrTranscriptAggregator aggregator = new AsrTranscriptAggregator();

    private final AtomicBoolean lifecycleStarted = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private String currentQuestionType = "PRINCIPLE";
    private String currentRoleHint = "";
    private String currentQuestionText = "";
    private String currentJobDescription = "";

    private AliyunAsrWebSocketClient aliyunClient;
    private AliyunAsrWebSocketClient.SessionConnection aliyunConnection;

    public AsrProxySessionBridge(
            String sessionId,
            AsrProxyTicketService.ClaimedTicket claimedTicket,
            Consumer<String> outboundEmitter,
            ObjectMapper objectMapper,
            SpeechProperties speechProperties,
            AsrConstrainedCorrectionService correctionService) {
        this.sessionId = sessionId;
        this.claimedTicket = claimedTicket;
        this.outboundEmitter = outboundEmitter;
        this.objectMapper = objectMapper;
        this.speechProperties = speechProperties;
        this.correctionService = correctionService;
    }

    public void setAliyunClient(AliyunAsrWebSocketClient aliyunClient) {
        this.aliyunClient = aliyunClient;
    }

    public void emitProxyError(String code, String message, boolean retryable) {
        emitError("proxy", code, message, currentTaskId(), retryable);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (aliyunConnection != null) {
            aliyunConnection.close();
        }
    }

    public void handleTextCommand(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            String type = root.path("type").asText("");
            log.info("[ASR-DEBUG] 收到浏览器控制消息, sessionId={}, type={}, payload={}", sessionId, type, text);
            if ("start".equals(type)) {
                if (!lifecycleStarted.compareAndSet(false, true)) {
                    emitError("proxy", "invalid_state", "一个连接只允许一次 start", currentTaskId(), false);
                    return;
                }
                String questionType = root.path("questionType").asText("").trim();
                if (!questionType.isEmpty()) {
                    currentQuestionType = questionType;
                }
                JsonNode context = root.path("context");
                currentRoleHint = context.path("roleHint").asText("").trim();
                currentQuestionText = context.path("questionText").asText("").trim();
                currentJobDescription = context.path("jobDescription").asText("").trim();
                log.info("[ASR-DEBUG] 开始建立代理 ASR 会话, sessionId={}, questionType={}", sessionId, currentQuestionType);
                startAliyunSession();
                return;
            }
            if ("stop".equals(type)) {
                if (aliyunConnection == null) {
                    emitError("proxy", "invalid_state", "ASR 会话尚未启动", currentTaskId(), false);
                    return;
                }
                log.info("[ASR-DEBUG] 收到 stop，准备结束阿里云 ASR 会话, sessionId={}, taskId={}", sessionId, currentTaskId());
                aliyunConnection.finish();
            }
        } catch (IOException e) {
            emitError("proxy", "invalid_message", e.getMessage(), currentTaskId(), false);
        }
    }

    public void handleBinaryAudio(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        if (aliyunConnection == null || !aliyunConnection.isReady()) {
            log.debug("[ASR-DEBUG] 音频帧被忽略（阿里云会话尚未 ready）, sessionId={}, bytes={}", sessionId, bytes.length);
            return;
        }
        aliyunConnection.sendAudio(bytes);
    }

    private void startAliyunSession() {
        if (aliyunClient == null) {
            emitError("proxy", "invalid_state", "阿里云 ASR 客户端未初始化", "", false);
            return;
        }
        try {
            aliyunConnection = aliyunClient.openSession(new AliyunAsrWebSocketClient.Listener() {
                @Override
                public void onTaskStarted(String taskId) {
                    log.info("[ASR-DEBUG] 代理会话 ready, sessionId={}, taskId={}", sessionId, taskId);
                    emit(Map.of("type", "ready", "taskId", taskId));
                }

                @Override
                public void onSentence(AsrTranscriptAggregator.SentenceResult sentence) {
                    log.info("[ASR-DEBUG] 代理收到句段, sessionId={}, taskId={}, text={}, sentenceEnd={}, heartbeat={}",
                            sessionId, currentTaskId(), sentence.text(), sentence.sentenceEnd(), sentence.heartbeat());
                    AsrTranscriptAggregator.ServerEvent event = aggregator.onSentence(sentence);
                    if (event == null) {
                        return;
                    }
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", event.type());
                    payload.put("text", event.text());
                    if (event.beginTime() != null) {
                        payload.put("beginTime", event.beginTime());
                    }
                    if (event.endTime() != null) {
                        payload.put("endTime", event.endTime());
                    }
                    emit(payload);
                }

                @Override
                public void onTaskFinished(String taskId) {
                    AsrTranscriptAggregator.FinalResult result = aggregator.finalizeResult(resolvePauseThresholdMs());
                    AsrConstrainedCorrectionService.CorrectionResult correctionResult = correctionService.correct(
                            result.text(),
                            new AsrConstrainedCorrectionService.CorrectionContext(
                                    currentRoleHint,
                                    currentQuestionText,
                                    currentJobDescription
                            )
                    );
                    log.info("[ASR-DEBUG] 代理会话 finished, sessionId={}, taskId={}, finalTextLength={}, segmentCount={}",
                            sessionId, taskId, correctionResult.correctedText().length(), result.asrSegments().size());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("type", "final");
                    payload.put("taskId", taskId);
                    payload.put("text", correctionResult.correctedText());
                    payload.put("rawText", correctionResult.rawText());
                    payload.put("changeList", correctionResult.changeList());
                    payload.put("correctionApplied", correctionResult.correctionApplied());
                    payload.put("pauseStats", result.pauseStats());
                    payload.put("asrSegments", result.asrSegments());
                    emit(payload);
                    close();
                }

                @Override
                public void onTaskFailed(String taskId, String code, String message) {
                    log.error("[ASR-DEBUG] 代理会话失败, sessionId={}, taskId={}, code={}, message={}",
                            sessionId, taskId, code, message);
                    emitError("aliyun_asr", code, message, taskId, true);
                    close();
                }

                @Override
                public void onClosed(String taskId, int statusCode, String reason) {
                    if (!closed.get()) {
                        emitError("aliyun_asr", "websocket_closed", reason, taskId, true);
                        close();
                    }
                }
            });
            log.info("ASR 代理会话已建立, sessionId={}, userId={}", sessionId, claimedTicket.getUserId());
        } catch (IOException e) {
            emitError("proxy", "websocket_closed", e.getMessage(), currentTaskId(), true);
            close();
        }
    }

    private int resolvePauseThresholdMs() {
        return speechProperties.getAsr().getPauseThresholdConfig()
                .getOrDefault(currentQuestionType, 2500);
    }

    private void emitError(String source, String code, String message, String taskId, boolean retryable) {
        emit(Map.of(
                "type", "error",
                "source", source,
                "code", code == null ? "" : code,
                "message", message == null ? "" : message,
                "taskId", taskId == null ? "" : taskId,
                "retryable", retryable
        ));
    }

    private void emit(Map<String, Object> payload) {
        try {
            outboundEmitter.accept(objectMapper.writeValueAsString(payload));
        } catch (IOException e) {
            log.error("ASR 代理事件序列化失败, sessionId={}", sessionId, e);
        } catch (RuntimeException e) {
            log.error("ASR 代理事件发送失败, sessionId={}", sessionId, e);
        }
    }

    private String currentTaskId() {
        return aliyunConnection != null ? aliyunConnection.taskId() : "";
    }
}
