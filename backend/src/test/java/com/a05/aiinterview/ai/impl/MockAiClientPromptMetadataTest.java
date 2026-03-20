package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    @DisplayName("callEvaluationDecision should populate retrieval intent for next question planning")
    void callEvaluationDecision_shouldPopulateRetrievalIntent() {
        PromptProperties promptProperties = new PromptProperties();
        promptProperties.setEvaluationDecision("v2");
        MockAiClient client = new MockAiClient(promptProperties);

        AiCallResult<EvaluationDecisionOutput> result = client.callEvaluationDecision(EvaluationDecisionInput.builder()
                .positionCode("JAVA_BACKEND")
                .experienceLevel("FRESH_GRAD")
                .mode("practice")
                .currentQuestionId(11L)
                .currentQuestionType("PRINCIPLE")
                .currentDomainCode("mysql")
                .currentDomainName("MySQL")
                .currentDomainId(2L)
                .currentTargetDepth("L1")
                .currentQuestionStem("MySQL 事务隔离级别有哪些？")
                .answerText("我大概记得几个，但事务边界有点忘了。")
                .stateLedger(Map.of("domain_states", List.of(
                        Map.of("domain_id", "mysql", "status", "IN_PROGRESS"),
                        Map.of("domain_id", "redis", "status", "UNASKED")
                )))
                .syllabusJson(Map.of("domains", List.of(
                        Map.of(
                                "domainId", 6L,
                                "domainCode", "redis",
                                "domainName", "Redis",
                                "targetDepth", "L2",
                                "focusPoints", List.of("缓存击穿")
                        )
                )))
                .build());

        assertThat(result.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(result.getPromptVersion()).isEqualTo("v2");
        assertThat(result.getOutput().getDecision()).isIn("rescue", "broaden", "followup", "wrapup");
        assertThat(result.getOutput().getRetrievalIntent()).isNotNull();
        assertThat(result.getOutput().getRetrievalIntent().getQuestionTypeHint()).isNotBlank();
        assertThat(result.getOutput().getRetrievalIntent().getFocusQuery()).isNotBlank();
    }
}
