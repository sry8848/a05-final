package com.a05.aiinterview.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prompt template coverage tests")
class PromptTemplateCoverageTest {

    private final PromptTemplateService service = new ClasspathPromptTemplateService(new ObjectMapper());

    @Test
    @DisplayName("question_generation_stream template should load and render")
    void renderQuestionGenerationStream_shouldLoad() {
        RenderedPrompt rendered = service.render("question_generation_stream", "v1", Map.of(
                "nextDomainName", "Concurrency",
                "nextDomainCode", "concurrency",
                "nextQuestionType", "PRINCIPLE",
                "targetDepth", "L3",
                "positionCode", "JAVA_BACKEND",
                "experienceLevel", "SENIOR",
                "mode", "professional",
                "askedQuestions", "- Explain thread pools",
                "syllabus", "{\"domains\":[]}"
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("question_generation_stream");
        assertThat(rendered.getUserPrompt()).contains("concurrency");
    }

    @Test
    @DisplayName("evaluation_decision template should load and render")
    void renderEvaluationDecision_shouldLoad() {
        RenderedPrompt rendered = service.render("evaluation_decision", "v1", Map.of(
                "evaluationPayload", "payload-content"
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(rendered.getUserPrompt()).contains("payload-content");
    }

    @Test
    @DisplayName("report_generation template should load and render")
    void renderReportGeneration_shouldLoad() {
        RenderedPrompt rendered = service.render("report_generation", "v1", Map.of(
                "reportPayload", "report-content"
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("report_generation");
        assertThat(rendered.getUserPrompt()).contains("report-content");
    }

    @Test
    @DisplayName("intro_rewrite template should load and render")
    void renderIntroRewrite_shouldLoad() {
        RenderedPrompt rendered = service.render("intro_rewrite", "v1", Map.of(
                "candidateContext", "context",
                "basePrompt", "base-prompt",
                "recentPrompts", "- q1\n- q2",
                "avoidPhrases", "- phrase-1"
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(rendered.getUserPrompt()).contains("base-prompt");
    }
}