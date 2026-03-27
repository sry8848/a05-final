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
                Map.entry("domains", "- code=concurrency, name=并发编程"),
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
                .contains("不要把整个知识域对象删掉")
                .contains("如果历史 discussedItems 为空，不要臆造项目去重信息")
                .contains("experienceItems 必须覆盖简历中所有真实存在")
                .contains("不允许把整个项目从 experienceItems 中删掉");
    }

    @Test
    @DisplayName("question_generation_stream template should load and render")
    void renderQuestionGenerationStream_shouldLoad() {
        RenderedPrompt rendered = service.render("question_generation_stream", "v2", Map.ofEntries(
                Map.entry("roleContext", "{\"roundType\":\"\",\"style\":\"guiding\"}"),
                Map.entry("projectContext", "{\"activeItemKey\":\"item_order\",\"itemType\":\"PROJECT\",\"itemName\":\"订单系统\",\"currentFocus\":\"线程池调优\"}"),
                Map.entry("recentContext", "{\"lastQuestion\":\"线程池参数怎么配？\",\"lastAnswerSummary\":\"候选人讲了核心参数，但拒绝策略和容量评估偏空。\",\"recentTurnsSummary\":\"最近两轮都在项目主线内追问。\",\"lastAnswerHighlights\":[\"corePoolSize\",\"队列容量\"]}"),
                Map.entry("nextQuestionGoal", "{\"questionType\":\"PRINCIPLE\",\"nextFocus\":\"拒绝策略与容量评估\",\"goalSummary\":\"继续验证线程池在项目里的取舍能力\",\"relatedDomainCode\":\"concurrency\",\"relatedDomainName\":\"Concurrency\",\"relatedItemKey\":\"item_order\",\"relatedItemType\":\"PROJECT\",\"relatedItemName\":\"订单系统\",\"expectedAnswerPoints\":[\"拒绝策略\",\"容量评估\"]}"),
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
        assertThat(rendered.getUserPrompt())
                .contains("下一问目标")
                .contains("拒绝策略与容量评估")
                .contains("{\"roundType\":\"\",\"style\":\"guiding\"}");
        assertThat(rendered.getSystemPrompt())
                .contains("efficiency")
                .contains("guiding")
                .contains("stress")
                .contains("2-6")
                .contains("不做精确概率控制")
                .contains("`retrievedMaterials` 非空时")
                .contains("`retrievalPlans` 仅在无真实材料时作为弱提示")
                .contains("`follow_up_ids`")
                .contains("项目题若无检索结果");
    }

    @Test
    @DisplayName("evaluation_decision template should load and render")
    void renderEvaluationDecision_shouldLoad() {
        RenderedPrompt rendered = service.render("evaluation_decision", "v2", Map.ofEntries(
                Map.entry("positionCode", "JAVA_BACKEND"),
                Map.entry("experienceLevel", "SENIOR"),
                Map.entry("roundType", ""),
                Map.entry("availableStrategies", """
1. 压测（StrategyCode: S_J_PRESSURE）：
- 意图：在真实工程链路中增加压力条件，考察候选人的边界判断与应对能力。
- 适用条件：
  当前点已具备继续加压的高信息增益。
2. 进入项目题（StrategyCode: S_ENTER_PROJECT）：
- 意图：进入项目主线，建立真实工程画像。
- 适用条件：
  项目仍然是当前信息密度最高的入口。
"""),
                Map.entry("remainingTargetDomains", """
1. Redis 缓存（domainCode: redis）
- 关联知识点：缓存击穿、缓存一致性
2. MySQL 数据库（domainCode: mysql）
- 关联知识点：幻读与间隙锁、覆盖索引
"""),
                Map.entry("currentQuestion", "{\"stem\":\"你刚才提到 Seata AT 模式，那具体讲讲全局事务和本地事务的边界。\",\"questionType\":\"PRINCIPLE\",\"domainCode\":\"spring\",\"domainName\":\"Spring 框架\",\"currentFocus\":\"Seata AT事务边界\",\"relatedItemKey\":\"\",\"relatedItemType\":\"\",\"relatedItemName\":\"\"}"),
                Map.entry("answerText", "回答内容"),
                Map.entry("expectedPoints", "- 参数含义\n- 调优思路"),
                Map.entry("projectAndInternshipSummary", "[{\"itemType\":\"PROJECT\",\"itemName\":\"订单系统\",\"resumeDescription\":\"负责订单链路\",\"techHooks\":[\"线程池调优\"],\"blockedEntryPoints\":[\"Redis 缓存一致性\"]}]"),
                Map.entry("coveredKnowledgeSummary", "[\"Java / 锁升级\"]"),
                Map.entry("crossSessionBlockedKnowledgePoints", "[\"Redis / 缓存击穿\"]"),
                Map.entry("questionIndex", 4),
                Map.entry("maxQuestions", 16),
                Map.entry("quotaSnapshot", "{\"samePointContinue\":{\"used\":1,\"max\":1},\"projectTotal\":{\"used\":2,\"max\":3}}"),
                Map.entry("retrievedMaterials", "[]"),
                Map.entry("recentInterviewMemory", "[]"),
                Map.entry("repairMode", "false"),
                Map.entry("repairAttemptNo", ""),
                Map.entry("rawDecisionOutput", ""),
                Map.entry("validationErrors", "[]")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("evaluation_decision");
        assertThat(rendered.getUserPrompt())
                .contains("Seata AT 模式")
                .contains("主考纲剩余待考察域（菜单）")
                .contains("redis")
                .contains("S_J_PRESSURE")
                .contains("S_ENTER_PROJECT")
                .contains("当前题号")
                .contains("4")
                .contains("16")
                .contains("samePointContinue")
                .contains("近期跨场禁选知识点")
                .contains("blockedEntryPoints")
                .contains("历史问题、回答概要、回答评价")
                .contains("不表示该项目本身被禁选")
                .doesNotContain("知识域及知识点状态")
                .doesNotContain("\"possibleNextMoves\"")
                .doesNotContain("\"newCandidatePointsByDomain\"")
                .doesNotContain("possibleNextMoves=[]")
                .doesNotContain("`possibleNextMoves`")
                .doesNotContain("`newCandidatePointsByDomain`")
                .doesNotContain("interviewGoalSummary")
                .doesNotContain("nextEntryAction")
                .doesNotContain("nextQuestionType");
        assertThat(rendered.getSystemPrompt())
                .contains("StrategyCode")
                .contains("只能从当前注入的策略池中选择一个 `finalDecision`")
                .contains("nextFocus")
                .contains("nextProjectPoint")
                .contains("nextItemType")
                .contains("nextItemName")
                .contains("goal")
                .contains("displayQuery")
                .contains("queryText")
                .contains("keywordHints")
                .contains("difficultyHint")
                .contains("mustHaveClues")
                .contains("avoidClues")
                .contains("检索 brief")
                .contains("技术钩子")
                .contains("软约束")
                .contains("`targetDomainCode` 必须从【主考纲剩余待考察域（菜单）】中选择一个合法的 `domainCode`")
                .contains("绝不能写成完整问句")
                .contains("绝不允许把下一题准备问的知识点提前预支写进去")
                .contains("\"domainCode\": \"redis\"")
                .doesNotContain("candidateStrategies")
                .doesNotContain("expectedAnswerPoints")
                .doesNotContain("nextQuestionType")
                .doesNotContain("nextEntryAction")
                .doesNotContain("DOMAIN_")
                .doesNotContain("primaryQuery")
                .doesNotContain("alternateQueries")
                .doesNotContain("retrievalType")
                .doesNotContain("expectedEvidence")
                .doesNotContain("avoidEvidence");
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
        RenderedPrompt rendered = service.render("intro_rewrite", "v2", Map.of(
                "candidateContext", "context",
                "interviewerArchetype", "stress",
                "basePrompt", "base-prompt",
                "recentPrompts", "- q1\n- q2",
                "avoidPhrases", "- phrase-1"
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("intro_rewrite");
        assertThat(rendered.getUserPrompt()).contains("base-prompt").contains("stress");
        assertThat(rendered.getSystemPrompt()).contains("efficiency").contains("guiding").contains("stress");
    }
}
