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
                Map.entry("roleContext", "{\"roundType\":\"technical_first\",\"candidateLevel\":\"SENIOR\",\"difficultyBand\":[\"L2\",\"L3\"],\"style\":\"natural_followup\"}"),
                Map.entry("projectContext", "{\"activeProjectId\":\"p_order\",\"projectName\":\"订单系统\",\"currentFocus\":\"线程池调优\"}"),
                Map.entry("recentContext", "{\"lastQuestion\":\"线程池参数怎么配？\",\"lastAnswerSummary\":\"候选人讲了核心参数，但拒绝策略和容量评估偏空。\",\"recentTurnsSummary\":\"最近两轮都在项目主线内追问。\",\"lastAnswerHighlights\":[\"corePoolSize\",\"队列容量\"]}"),
                Map.entry("nextQuestionGoal", "{\"decision\":\"followup\",\"targetFocus\":\"拒绝策略与容量评估\",\"targetAngle\":\"tradeoff\",\"difficultyAdjustment\":\"same\",\"questionType\":\"PRINCIPLE\",\"focusPoint\":\"线程池拒绝策略\",\"nextQuestionGoal\":\"继续验证线程池在项目里的取舍能力\",\"nextDomainCode\":\"concurrency\",\"nextDomainName\":\"Concurrency\"}"),
                Map.entry("retrievalContext", "{\"query\":\"线程池拒绝策略 容量评估\",\"ragContext\":\"项目里使用线程池处理异步通知。\",\"domainHint\":\"concurrency\",\"questionTypeHint\":\"PRINCIPLE\",\"avoidRecentFamilies\":[\"concurrency.threadpool.definition\"]}"),
                Map.entry("constraints", "{\"avoidRepetitionFamilies\":[\"concurrency.threadpool.definition\"],\"mustSoundNatural\":true,\"maxSentences\":2}"),
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("mode", "professional"),
                Map.entry("askedQuestions", "- Explain thread pools\n- Explain lock contention"),
                Map.entry("resumeTextSummary", "候选人负责高并发订单服务和异步任务调度。"),
                Map.entry("syllabus", "{\"domains\":[]}")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("question_generation_stream");
        assertThat(rendered.getUserPrompt()).contains("下一问目标").contains("线程池拒绝策略");
    }

    @Test
    @DisplayName("evaluation_decision template should load and render")
    void renderEvaluationDecision_shouldLoad() {
        RenderedPrompt rendered = service.render("evaluation_decision", "v2", Map.ofEntries(
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("mode", "professional"),
                Map.entry("interviewHardConstraints", "{\"difficultyBand\":[\"L2\",\"L3\"],\"requiredDomains\":[\"concurrency\",\"mysql\"],\"remainingTurnBudget\":6}"),
                Map.entry("currentQuestionStem", "请解释线程池参数。"),
                Map.entry("currentDomainName", "并发编程"),
                Map.entry("currentDomainCode", "concurrency"),
                Map.entry("currentTargetDepth", "L3"),
                Map.entry("currentQuestionType", "PRINCIPLE"),
                Map.entry("answerText", "回答内容"),
                Map.entry("resumeText", "候选人做过订单系统与缓存优化"),
                Map.entry("expectedPoints", "- 参数含义\n- 调优思路"),
                Map.entry("recentContext", "- [PRINCIPLE/concurrency] Q: 讲讲锁升级 | A: ..."),
                Map.entry("currentInterviewContext", "{\"activeProjectId\":\"p_order\",\"currentFocus\":\"线程池调优\",\"coveredDomains\":[\"java_basic\"],\"coveredPoints\":[\"concurrency:thread-pool-basic\"],\"weakSignals\":[\"容量评估偏空\"],\"recentQuestionFamilies\":[\"concurrency.threadpool.definition\"]}"),
                Map.entry("pauseStats", "无"),
                Map.entry("stateLedgerJson", "{\"asked_total\":1}"),
                Map.entry("syllabusJson", "{\"domains\":[]}"),
                Map.entry("outputSchema", "{\"type\":\"object\"}")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(rendered.getUserPrompt()).contains("请解释线程池参数").contains("当前面试语境");
    }

    @Test
    @DisplayName("report_generation template should load and render")
    void renderReportGeneration_shouldLoad() {
        RenderedPrompt rendered = service.render("report_generation", "v1", Map.of(
                "positionCode", "JAVA_BACKEND",
                "experienceLevel", "SENIOR",
                "mode", "professional",
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
