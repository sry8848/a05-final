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
    @DisplayName("planner template should load and render")
    void renderPlanner_shouldLoad() {
        RenderedPrompt rendered = service.render("planner", "v2", Map.ofEntries(
                Map.entry("position", "Java 后端开发"),
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("roundType", ""),
                Map.entry("mode", "professional"),
                Map.entry("jd", "负责高并发订单系统研发。"),
                Map.entry("resumeText", "候选人负责订单、支付和缓存优化。"),
                Map.entry("focusTopics", "并发、缓存"),
                Map.entry("domains", "- id=1, code=concurrency, name=并发编程"),
                Map.entry("historyInterviews", "[]")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("planner");
        assertThat(rendered.getUserPrompt()).contains("Java 后端开发")
                .contains("面试轮次")
                .contains("负责高并发订单系统研发");
        assertThat(rendered.getSystemPrompt())
                .contains("5~8 个")
                .contains("只能从输入提供的岗位知识域列表中选择")
                .contains("coveredKnowledgePoints")
                .contains("不要把整个 domain 删掉")
                .contains("如果历史 discussedItems 为空，不要臆造项目去重信息");
    }

    @Test
    @DisplayName("question_generation_stream template should load and render")
    void renderQuestionGenerationStream_shouldLoad() {
        RenderedPrompt rendered = service.render("question_generation_stream", "v1", Map.ofEntries(
                Map.entry("roleContext", "{\"roundType\":\"\",\"candidateLevel\":\"SENIOR\",\"style\":\"natural_followup\"}"),
                Map.entry("projectContext", "{\"activeItemKey\":\"item_order\",\"itemType\":\"PROJECT\",\"itemName\":\"订单系统\",\"currentFocus\":\"线程池调优\"}"),
                Map.entry("recentContext", "{\"lastQuestion\":\"线程池参数怎么配？\",\"lastAnswerSummary\":\"候选人讲了核心参数，但拒绝策略和容量评估偏空。\",\"recentTurnsSummary\":\"最近两轮都在项目主线内追问。\",\"lastAnswerHighlights\":[\"corePoolSize\",\"队列容量\"]}"),
                Map.entry("nextQuestionGoal", "{\"questionType\":\"PRINCIPLE\",\"nextFocus\":\"拒绝策略与容量评估\",\"goalSummary\":\"继续验证线程池在项目里的取舍能力\",\"relatedDomainId\":1,\"relatedDomainCode\":\"concurrency\",\"relatedDomainName\":\"Concurrency\",\"relatedItemKey\":\"item_order\",\"relatedItemType\":\"PROJECT\",\"relatedItemName\":\"订单系统\",\"expectedAnswerPoints\":[\"拒绝策略\",\"容量评估\"]}"),
                Map.entry("retrievalContext", "{\"summary\":\"无外部参考资料，请严格依赖你自身的工程师知识库进行出题。\",\"retrievalPlans\":[],\"retrievedMaterials\":[]}"),
                Map.entry("constraints", "{\"avoidRepetitionFamilies\":[\"concurrency.threadpool.definition\"],\"mustSoundNatural\":true,\"maxSentences\":2}"),
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("mode", "professional"),
                Map.entry("askedQuestions", "- Explain thread pools\n- Explain lock contention"),
                Map.entry("resumeTextSummary", "候选人负责高并发订单服务和异步任务调度。"),
                Map.entry("syllabus", "{\"domains\":[]}")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("question_generation_stream");
        assertThat(rendered.getUserPrompt()).contains("下一问目标").contains("拒绝策略与容量评估");
    }

    @Test
    @DisplayName("evaluation_decision template should load and render")
    void renderEvaluationDecision_shouldLoad() {
        RenderedPrompt rendered = service.render("evaluation_decision", "v2", Map.ofEntries(
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("roundType", ""),
                Map.entry("currentQuestion", "{\"stem\":\"请先做一个简短的自我介绍。\",\"questionType\":\"INTRO\",\"domainId\":null,\"domainName\":\"\",\"currentFocus\":\"\",\"relatedItemKey\":\"\",\"relatedItemType\":\"\",\"relatedItemName\":\"\"}"),
                Map.entry("answerText", "回答内容"),
                Map.entry("expectedPoints", "- 参数含义\n- 调优思路"),
                Map.entry("projectAndInternshipSummary", "[{\"itemType\":\"PROJECT\",\"itemName\":\"订单系统\",\"resumeDescription\":\"负责订单链路\",\"techHooks\":[\"线程池调优\"]}]"),
                Map.entry("interviewGoalSummary", "{\"domains\":[{\"domainId\":1,\"domainCode\":\"concurrency\",\"domainName\":\"并发编程\",\"focusPoints\":[\"线程池参数\"],\"status\":\"UNASKED\"}]}"),
                Map.entry("coveredKnowledgeSummary", "[\"Java / 锁升级\"]"),
                Map.entry("quotaSummary", "{\"samePointContinue\":{\"count\":1,\"maxCount\":20},\"sameDomainContinue\":{\"count\":1,\"maxCount\":20},\"sameProjectPointContinue\":{\"count\":0,\"maxCount\":20},\"sameProjectContinue\":{\"count\":0,\"maxCount\":20},\"principleTotal\":{\"count\":1,\"maxCount\":20},\"projectTotal\":{\"count\":0,\"maxCount\":20},\"scenarioTotal\":{\"count\":0,\"maxCount\":20},\"behavioralTotal\":{\"count\":0,\"maxCount\":20}}"),
                Map.entry("possibleFutureDirections", "[\"PRINCIPLE: 线程池拒绝策略\",\"PROJECT_DEEP_DIVE: 回到订单系统线程池调优\"]"),
                Map.entry("retrievedMaterials", "[]"),
                Map.entry("recentInterviewMemory", "[]"),
                Map.entry("outputSchema", "{\"type\":\"object\"}")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(rendered.getUserPrompt())
                .contains("请先做一个简短的自我介绍。")
                .contains("历史问题、回答概要、回答评价")
                .contains("开放项目题、设计题、系统脆弱点题，不强制绑定单一知识域")
                .contains("如果 `currentQuestion.questionType == INTRO`")
                .contains("只能选择“退出当前题类”")
                .contains("如果 `currentQuestion.questionType == PRINCIPLE` 且 `nextQuestionType == PRINCIPLE`")
                .contains("nextFocus 必须是单一焦点短语")
                .contains("nextEntryAction")
                .contains("退出当前题类")
                .contains("不能写成完整问句")
                .contains("只写本轮已经形成判断的事实")
                .contains("不要把下一题准备问的点提前写进")
                .doesNotContain("\"possibleNextMoves\"")
                .doesNotContain("\"newCandidatePointsByDomain\"")
                .doesNotContain("possibleNextMoves=[]")
                .doesNotContain("`possibleNextMoves`")
                .doesNotContain("`newCandidatePointsByDomain`");
        assertThat(rendered.getSystemPrompt())
                .contains("理论题禁止混入实战类动作");
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
