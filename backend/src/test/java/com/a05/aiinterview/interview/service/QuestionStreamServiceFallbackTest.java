package com.a05.aiinterview.interview.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.URI;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionStreamServiceFallbackTest {

    @Test
    void shouldUseFallbackQuestionGeneration_whenDnsResolutionFails() {
        Throwable error = new WebClientRequestException(
                new UnknownHostException("dashscope.aliyuncs.com"),
                null,
                URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
                HttpHeaders.EMPTY
        );

        assertTrue(QuestionStreamService.shouldUseFallbackQuestionGeneration(error));
    }

    @Test
    void shouldUseFallbackQuestionGeneration_whenErrorIsBusinessValidation() {
        assertFalse(QuestionStreamService.shouldUseFallbackQuestionGeneration(
                new IllegalArgumentException("prompt is invalid")
        ));
    }

    @Test
    void buildFallbackQuestionStem_shouldGenerateUsablePrincipleQuestion() {
        String stem = QuestionStreamService.buildFallbackQuestionStem(
                "PRINCIPLE",
                "缓存一致性",
                "缓存一致性",
                "JAVA_BACKEND"
        );

        assertTrue(stem.contains("缓存一致性"));
        assertTrue(stem.contains("原理") || stem.contains("场景"));
    }

    @Test
    void buildFallbackQuestionStem_shouldGenerateUsableIntroQuestion() {
        String stem = QuestionStreamService.buildFallbackQuestionStem(
                "INTRO",
                null,
                null,
                "JAVA_BACKEND"
        );

        assertTrue(stem.contains("自我介绍"));
        assertTrue(stem.contains("后端"));
    }
}
