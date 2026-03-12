package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockAiClient prompt metadata tests")
class MockAiClientPromptMetadataTest {

    @Test
    @DisplayName("callPlanner should return configured prompt metadata")
    void callPlanner_shouldReturnConfiguredPromptMetadata() {
        PromptProperties promptProperties = new PromptProperties();
        promptProperties.setPlanner("v8");
        MockAiClient client = new MockAiClient(promptProperties);

        AiCallResult<PlannerOutput> result = client.callPlanner(PlannerInput.builder()
                .positionName("Java 后端")
                .positionCode("JAVA_BACKEND")
                .experienceLevel("SENIOR")
                .mode("professional")
                .build());

        assertThat(result.getPromptCode()).isEqualTo("planner");
        assertThat(result.getPromptVersion()).isEqualTo("v8");
    }

    @Test
    @DisplayName("callIntroRewrite should return configured prompt metadata")
    void callIntroRewrite_shouldReturnConfiguredPromptMetadata() {
        PromptProperties promptProperties = new PromptProperties();
        promptProperties.setIntroRewrite("v9");
        MockAiClient client = new MockAiClient(promptProperties);

        AiCallResult<String> result = client.callIntroRewrite(IntroRewriteInput.builder()
                .positionCode("JAVA_BACKEND")
                .experienceLevel("SENIOR")
                .mode("professional")
                .basePrompt("请做一个自我介绍")
                .recentPrompts(java.util.List.of())
                .avoidPhrases(java.util.List.of())
                .build());

        assertThat(result.getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(result.getPromptVersion()).isEqualTo("v9");
        assertThat(result.getOutput()).isNotBlank();
    }
}
