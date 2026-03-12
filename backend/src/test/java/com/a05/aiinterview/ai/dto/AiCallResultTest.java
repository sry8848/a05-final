package com.a05.aiinterview.ai.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AiCallResult tests")
class AiCallResultTest {

    @Test
    @DisplayName("builder should retain prompt metadata")
    void builder_shouldRetainPromptMetadata() {
        AiCallResult<String> result = AiCallResult.<String>builder()
                .output("ok")
                .promptCode("planner")
                .promptVersion("v2")
                .promptTokens(12)
                .responseTokens(34)
                .latencyMs(56L)
                .build();

        assertThat(result.getOutput()).isEqualTo("ok");
        assertThat(result.getPromptCode()).isEqualTo("planner");
        assertThat(result.getPromptVersion()).isEqualTo("v2");
        assertThat(result.getPromptTokens()).isEqualTo(12);
        assertThat(result.getResponseTokens()).isEqualTo(34);
        assertThat(result.getLatencyMs()).isEqualTo(56L);
    }
}
