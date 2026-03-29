package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtsServiceWebSocketProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveTtsWebSocketEndpoint_shouldConvertHttpSpeechEndpointToOfficialInferenceUrl() {
        TtsService ttsService = new TtsService(new SpeechProperties(), new StringRedisTemplate(), objectMapper);

        String resolved = ttsService.resolveTtsWebSocketEndpoint(
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2speech/speech-synthesis");

        assertEquals("wss://dashscope.aliyuncs.com/api-ws/v1/inference", resolved);
    }

    @Test
    void resolveTtsWebSocketEndpoint_shouldKeepExplicitWebSocketUrl() {
        TtsService ttsService = new TtsService(new SpeechProperties(), new StringRedisTemplate(), objectMapper);

        String resolved = ttsService.resolveTtsWebSocketEndpoint(
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference");

        assertEquals("wss://dashscope.aliyuncs.com/api-ws/v1/inference", resolved);
    }

    @Test
    void buildRunTaskPayload_shouldFollowOfficialDuplexProtocol() throws Exception {
        SpeechProperties properties = new SpeechProperties();
        properties.getTts().setModel("cosyvoice-v3-flash");
        properties.getTts().setVoice("longanyang");
        TtsService ttsService = new TtsService(properties, new StringRedisTemplate(), objectMapper);
        String taskId = "task-123";

        String payload = ttsService.buildRunTaskPayload(taskId);
        JsonNode root = objectMapper.readTree(payload);

        assertEquals("run-task", root.path("header").path("action").asText());
        assertEquals(taskId, root.path("header").path("task_id").asText());
        assertEquals("duplex", root.path("header").path("streaming").asText());
        assertEquals("audio", root.path("payload").path("task_group").asText());
        assertEquals("tts", root.path("payload").path("task").asText());
        assertEquals("SpeechSynthesizer", root.path("payload").path("function").asText());
        assertEquals("cosyvoice-v3-flash", root.path("payload").path("model").asText());
        assertEquals("longanyang", root.path("payload").path("parameters").path("voice").asText());
        assertEquals("mp3", root.path("payload").path("parameters").path("format").asText());
        assertEquals("PlainText", root.path("payload").path("parameters").path("text_type").asText());
        assertTrue(root.path("payload").path("input").isObject());
    }

    @Test
    void buildContinueTaskPayload_shouldWrapPlainTextInput() throws Exception {
        TtsService ttsService = new TtsService(new SpeechProperties(), new StringRedisTemplate(), objectMapper);
        String taskId = "task-456";

        String payload = ttsService.buildContinueTaskPayload(taskId, "你好，面试开始。");
        JsonNode root = objectMapper.readTree(payload);

        assertEquals("continue-task", root.path("header").path("action").asText());
        assertEquals(taskId, root.path("header").path("task_id").asText());
        assertEquals("duplex", root.path("header").path("streaming").asText());
        assertEquals("你好，面试开始。", root.path("payload").path("input").path("text").asText());
    }

    @Test
    void buildFinishTaskPayload_shouldRequestTaskTermination() throws Exception {
        TtsService ttsService = new TtsService(new SpeechProperties(), new StringRedisTemplate(), objectMapper);
        String taskId = "task-789";

        String payload = ttsService.buildFinishTaskPayload(taskId);
        JsonNode root = objectMapper.readTree(payload);

        assertEquals("finish-task", root.path("header").path("action").asText());
        assertEquals(taskId, root.path("header").path("task_id").asText());
        assertEquals("duplex", root.path("header").path("streaming").asText());
        assertTrue(root.path("payload").path("input").isObject());
    }
}
