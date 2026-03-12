package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import com.a05.aiinterview.speech.service.TtsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.codec.ServerSentEvent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionStreamServiceReconnectTest {

    @SuppressWarnings("unchecked")
    @Test
    void streamQuestion_shouldReplayCachedDeltaWhenGenerationAlreadyStreaming() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        RagRetrievalService ragService = mock(RagRetrievalService.class);
        TtsService ttsService = mock(TtsService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForList()).thenReturn(listOps);

        String attemptId = "attempt-1";
        String statusKey = "sse:gen:%s:status".formatted(attemptId);
        String chunksKey = "sse:gen:%s:chunks".formatted(attemptId);

        AtomicInteger statusReadCount = new AtomicInteger(0);
        when(valueOps.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            if (statusKey.equals(key)) {
                return statusReadCount.getAndIncrement() == 0 ? "streaming" : "done";
            }
            return null;
        });
        when(listOps.range(eq(chunksKey), anyLong(), eq(-1L))).thenAnswer(invocation -> {
            long from = invocation.getArgument(1, Long.class);
            return from <= 0 ? List.of("缓存片段") : List.of();
        });
        when(listOps.range(eq("sse:gen:%s:ttsReady".formatted(attemptId)), anyLong(), eq(-1L)))
                .thenReturn(List.of());

        QuestionStreamService service = new QuestionStreamService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                ragService,
                ttsService,
                redisTemplate,
                new ObjectMapper()
        );

        ServerSentEvent<String> firstEvent = service.streamQuestion(1L, 1L, attemptId, null)
                .blockFirst(Duration.ofSeconds(2));

        assertNotNull(firstEvent);
        assertEquals("delta", firstEvent.event());
        assertTrue(firstEvent.data() != null && firstEvent.data().contains("缓存片段"));
    }
}
