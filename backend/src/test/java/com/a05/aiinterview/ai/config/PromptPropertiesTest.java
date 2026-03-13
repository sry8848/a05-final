package com.a05.aiinterview.ai.config;

import com.a05.aiinterview.ai.prompt.PromptCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PromptProperties tests")
class PromptPropertiesTest {

    @Test
    @DisplayName("default versions should be v1")
    void defaultVersions_shouldBeV1() {
        PromptProperties properties = new PromptProperties();

        assertThat(properties.getPlanner()).isEqualTo("v1");
        assertThat(properties.getQuestionGenerationStream()).isEqualTo("v1");
        assertThat(properties.getEvaluationDecision()).isEqualTo("v1");
        assertThat(properties.getReportGeneration()).isEqualTo("v1");
        assertThat(properties.getIntroRewrite()).isEqualTo("v1");
    }

    @Test
    @DisplayName("resolveVersion should cover all supported prompt codes")
    void resolveVersion_shouldCoverAllSupportedPromptCodes() {
        PromptProperties properties = new PromptProperties();
        properties.setPlanner("v11");
        properties.setQuestionGenerationStream("v12");
        properties.setEvaluationDecision("v13");
        properties.setReportGeneration("v14");
        properties.setIntroRewrite("v15");

        assertThat(properties.asVersionMap()).isEqualTo(Map.of(
                PromptCode.PLANNER, "v11",
                PromptCode.QUESTION_GENERATION_STREAM, "v12",
                PromptCode.EVALUATION_DECISION, "v13",
                PromptCode.REPORT_GENERATION, "v14",
                PromptCode.INTRO_REWRITE, "v15"
        ));
        assertThat(properties.resolveVersion(PromptCode.PLANNER)).isEqualTo("v11");
        assertThat(properties.resolveVersion(PromptCode.QUESTION_GENERATION_STREAM)).isEqualTo("v12");
        assertThat(properties.resolveVersion(PromptCode.EVALUATION_DECISION)).isEqualTo("v13");
        assertThat(properties.resolveVersion(PromptCode.REPORT_GENERATION)).isEqualTo("v14");
        assertThat(properties.resolveVersion(PromptCode.INTRO_REWRITE)).isEqualTo("v15");
    }

    @Test
    @DisplayName("resolveVersion should reject unsupported prompt code")
    void resolveVersion_shouldRejectUnsupportedPromptCode() {
        PromptProperties properties = new PromptProperties();

        assertThatThrownBy(() -> properties.resolveVersion("unknown_prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown_prompt");
    }
}
