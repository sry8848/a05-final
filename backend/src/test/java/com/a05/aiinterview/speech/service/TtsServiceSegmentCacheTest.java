package com.a05.aiinterview.speech.service;

import com.a05.aiinterview.speech.config.SpeechProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TtsServiceSegmentCacheTest {

    @Test
    void triggerQuestionSegmentAudioAsync_shouldReturnReadyWhenTextCacheHit() throws Exception {
        SpeechProperties properties = new SpeechProperties();
        properties.getTts().setEnabled(true);
        properties.getTts().setCacheTtlSeconds(3600);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String normalizedText = "你好，世界。";
        String hash = sha256(normalizedText);
        when(valueOps.get(eq("speech:tts:text:" + hash))).thenReturn(Base64.getEncoder().encodeToString("ok".getBytes(StandardCharsets.UTF_8)));

        TtsService ttsService = new TtsService(properties, redisTemplate, new ObjectMapper());

        boolean ready = ttsService.triggerQuestionSegmentAudioAsync(1L, "attempt-1", 0, normalizedText).get();

        assertTrue(ready);
        verify(valueOps).set(eq("speech:tts:attempt:1:attempt-1:segment:0"), eq(hash), eq(3600L), eq(TimeUnit.SECONDS));
        verify(valueOps).set(eq("speech:tts:attempt:1:attempt-1:segment:0:state"), eq("ready"), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void getAttemptSegmentAudioBytes_shouldReadByAttemptSegmentIndex() {
        SpeechProperties properties = new SpeechProperties();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        when(valueOps.get("speech:tts:attempt:1:attempt-1:segment:2")).thenReturn("h1");
        when(valueOps.get("speech:tts:text:h1")).thenReturn(Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)));

        TtsService ttsService = new TtsService(properties, redisTemplate, new ObjectMapper());

        byte[] bytes = ttsService.getAttemptSegmentAudioBytes(1L, "attempt-1", 2);

        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), bytes);
    }

    private String sha256(String text) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
