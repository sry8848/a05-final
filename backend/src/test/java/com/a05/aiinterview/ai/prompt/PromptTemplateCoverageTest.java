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
        RenderedPrompt rendered = service.render("question_generation_stream", "v1", Map.ofEntries(
                Map.entry("nextDomainName", "Concurrency"),
                Map.entry("nextDomainCode", "concurrency"),
                Map.entry("nextQuestionType", "PRINCIPLE"),
                Map.entry("targetDepth", "L3"),
                Map.entry("difficulty", "L3"),
                Map.entry("targetSkill", "线程池调优"),
                Map.entry("expectedPoints", "- corePoolSize\n- 拒绝策略"),
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("mode", "professional"),
                Map.entry("ragContext", "无外部参考资料，请严格依赖你自身的工程师知识库进行出题。"),
                Map.entry("askedQuestions", "- Explain thread pools\n- Explain lock contention"),
                Map.entry("syllabus", "{\"domains\":[]}")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("question_generation_stream");
        assertThat(rendered.getUserPrompt()).contains("concurrency");
    }

    @Test
    @DisplayName("evaluation_decision template should load and render")
    void renderEvaluationDecision_shouldLoad() {
        RenderedPrompt rendered = service.render("evaluation_decision", "v1", Map.ofEntries(
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("mode", "professional"),
                Map.entry("currentQuestionStem", "请解释线程池参数。"),
                Map.entry("currentDomainName", "并发编程"),
                Map.entry("currentDomainCode", "concurrency"),
                Map.entry("currentTargetDepth", "L3"),
                Map.entry("currentQuestionType", "PRINCIPLE"),
                Map.entry("answerText", "回答内容"),
                Map.entry("expectedPoints", "- 参数含义\n- 调优思路"),
                Map.entry("recentContext", "- [PRINCIPLE/concurrency] Q: 讲讲锁升级 | A: ..."),
                Map.entry("pauseStats", "无"),
                Map.entry("stateLedgerJson", "{\"asked_total\":1}"),
                Map.entry("syllabusJson", "{\"domains\":[]}"),
                Map.entry("outputSchema", "{\"type\":\"object\"}")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(rendered.getUserPrompt()).contains("请解释线程池参数");
    }

    @Test
    @DisplayName("report_generation template should load and render")
    void renderReportGeneration_shouldLoad() {
        RenderedPrompt rendered = service.render("report_generation", "v1", Map.of(
                "positionCode", "JAVA_BACKEND",
                "experienceLevel", "SENIOR",
                "sessionTitle", "Java 后端模拟面试",
                "qaPairs", "题型: PRINCIPLE\n题目: 请解释线程池参数。",
                "stateLedgerJson", "{\"asked_total\":3}",
                "syllabusJson", "{\"domains\":[]}",
                "outputSchema", "{\"type\":\"object\"}"
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("report_generation");
        assertThat(rendered.getUserPrompt()).contains("Java 后端模拟面试");
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
