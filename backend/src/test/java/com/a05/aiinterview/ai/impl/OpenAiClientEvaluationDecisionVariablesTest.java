package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.contract.AiOutputContractValidator;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.ai.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("OpenAiClient evaluation-decision variable tests")
class OpenAiClientEvaluationDecisionVariablesTest {

    @Test
    @DisplayName("buildEvaluationDecisionVariables should include progress and quota snapshot")
    void buildEvaluationDecisionVariables_shouldIncludeProgressAndQuotaSnapshot() {
        OpenAiClient client = new OpenAiClient(
                mock(ChatModel.class),
                mock(PromptTemplateService.class),
                new PromptProperties(),
                new ObjectMapper(),
                new AiOutputContractValidator(new ObjectMapper())
        );

        EvaluationDecisionInput input = EvaluationDecisionInput.builder()
                .interview(EvaluationDecisionInput.InterviewMeta.builder()
                        .positionCode("JAVA_BACKEND")
                        .experienceLevel("JUNIOR")
                        .roundType("")
                        .build())
                .questionIndex(4)
                .maxQuestions(15)
                .quotaSnapshot(new LinkedHashMap<>(Map.of(
                        "samePointContinue", EvaluationDecisionInput.QuotaSnapshotItem.builder()
                                .used(1)
                                .max(1)
                                .build()
                )))
                .projectAndInternshipSummary(List.of())
                .remainingTargetDomains(List.of())
                .coveredKnowledgeSummary(List.of())
                .availableStrategies(List.of())
                .currentQuestion(EvaluationDecisionInput.CurrentQuestionContext.builder().build())
                .answerText("回答")
                .expectedPoints(List.of("定义"))
                .retrievedMaterials(List.of())
                .recentInterviewMemory(List.of())
                .repairMode(false)
                .rawDecisionOutput("")
                .validationErrors(List.of())
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> variables = ReflectionTestUtils.invokeMethod(
                client,
                "buildEvaluationDecisionVariables",
                input,
                "{\"type\":\"object\"}"
        );

        assertThat(variables.get("questionIndex")).isEqualTo("4");
        assertThat(variables.get("maxQuestions")).isEqualTo("15");
        assertThat(String.valueOf(variables.get("quotaSnapshot")))
                .contains("\"samePointContinue\"")
                .contains("\"used\":1")
                .contains("\"max\":1");
    }

    @Test
    @DisplayName("buildQuestionRoleContext should not duplicate experienceLevel into roleContext")
    void buildQuestionRoleContext_shouldNotDuplicateExperienceLevelIntoRoleContext() {
        OpenAiClient client = new OpenAiClient(
                mock(ChatModel.class),
                mock(PromptTemplateService.class),
                new PromptProperties(),
                new ObjectMapper(),
                new AiOutputContractValidator(new ObjectMapper())
        );

        QuestionGenerationInput input = QuestionGenerationInput.builder()
                .experienceLevel("SENIOR")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> roleContext = ReflectionTestUtils.invokeMethod(
                client,
                "buildQuestionRoleContext",
                input
        );

        assertThat(roleContext)
                .containsEntry("roundType", "")
                .containsEntry("style", "efficiency")
                .containsOnlyKeys("roundType", "style");
    }
}
