package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliyunAsrWebSocketClientTest {

    @Test
    void buildRunTaskPayload_shouldIncludeChineseRealtimeTuningParameters() throws Exception {
        AliyunAsrWebSocketClient client = new AliyunAsrWebSocketClient(new SpeechProperties(), new ObjectMapper());

        Method buildRunTaskPayload = AliyunAsrWebSocketClient.class
                .getDeclaredMethod("buildRunTaskPayload", String.class);
        buildRunTaskPayload.setAccessible(true);

        String payload = (String) buildRunTaskPayload.invoke(client, "task-1");
        JsonNode parameters = new ObjectMapper()
                .readTree(payload)
                .path("payload")
                .path("parameters");

        assertEquals("pcm", parameters.path("format").asText());
        assertEquals(16000, parameters.path("sample_rate").asInt());
        assertEquals("zh", parameters.path("language_hints").get(0).asText());
        assertTrue(parameters.path("punctuation_prediction_enabled").asBoolean());
        assertTrue(parameters.path("inverse_text_normalization_enabled").asBoolean());
        assertTrue(parameters.path("disfluency_removal_enabled").asBoolean());
        assertTrue(parameters.path("multi_threshold_mode_enabled").asBoolean());
        assertEquals(1500, parameters.path("max_sentence_silence").asInt());
        assertFalse(parameters.path("semantic_punctuation_enabled").asBoolean());
    }
}
