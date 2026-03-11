package com.a05.aiinterview.ai.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptProperties tests")
class PromptPropertiesTest {

    @Test
    @DisplayName("default versions should be v1")
    void defaultVersions_shouldBeV1() {
        PromptProperties properties = new PromptProperties();

        assertThat(properties.getPlanner()).isEqualTo("v1");
        assertThat(properties.getQuestionGeneration()).isEqualTo("v1");
        assertThat(properties.getQuestionGenerationStream()).isEqualTo("v1");
        assertThat(properties.getEvaluationDecision()).isEqualTo("v1");
        assertThat(properties.getReportGeneration()).isEqualTo("v1");
        assertThat(properties.getIntroRewrite()).isEqualTo("v1");
    }
}