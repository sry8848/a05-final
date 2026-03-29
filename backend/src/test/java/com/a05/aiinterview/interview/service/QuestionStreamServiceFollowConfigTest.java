package com.a05.aiinterview.interview.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionStreamServiceFollowConfigTest {

    @Test
    void resolveFollowPollInterval_shouldUseConfiguredMillisWithFloorProtection() {
        assertEquals(Duration.ofMillis(250), QuestionStreamService.resolveFollowPollInterval(250));
        assertEquals(Duration.ofMillis(50), QuestionStreamService.resolveFollowPollInterval(0));
        assertEquals(Duration.ofMillis(50), QuestionStreamService.resolveFollowPollInterval(-10));
    }

    @Test
    void resolveFollowIdleTimeout_shouldUseConfiguredSecondsWithFloorProtection() {
        assertEquals(Duration.ofSeconds(30), QuestionStreamService.resolveFollowIdleTimeout(30));
        assertEquals(Duration.ofSeconds(5), QuestionStreamService.resolveFollowIdleTimeout(0));
        assertEquals(Duration.ofSeconds(5), QuestionStreamService.resolveFollowIdleTimeout(-3));
    }
}
