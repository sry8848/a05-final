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
                .contains("不允许把整个项目从 experienceItems 中删掉")
                .contains("必须复述简历中该项目/实习条目的对应原文片段")
                .doesNotContain("简历上的原始项目描述");
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
                .contains("默认直接发问，不加前缀")
                .contains("recentContext")
                .contains("askedQuestions")
                .contains("候选人上一轮若回答极短、空泛、敷衍")
                .contains("异步线程处理评分结果时，线程间怎么安全传递？")
                .contains("不做精确概率控制")
                .contains("`retrievedMaterials` 非空时")
                .contains("`retrievalPlans` 仅在无真实材料时作为弱提示")
                .contains("`follow_up_ids`")
                .contains("PROJECT_DEEP_DIVE")
                .contains("PRINCIPLE")
                .contains("StringBuilder 和 StringBuffer 的线程安全差别是什么？")
                .contains("在你的 AI 面试系统里，StringBuilder 和 StringBuffer 怎么选？")
                .doesNotContain("若当前题型是 PROJECT_DEEP_DIVE，或 projectContext 明确存在");
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
                .contains("当前待评估题")
                .contains("候选人当前原始回答")
                .contains("当前题参考锚点")
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
                .contains("历史问答记录")
                .contains("最近 3 题保留原始问答")
                .contains("其余更早题目只保留问题和回答摘要")
                .contains("当前轮严格合法的动作集合，不是推荐顺序")
                .contains("先理解其会话含义")
                .contains("不表示该项目本身被禁选")
                .doesNotContain("【上一题】")
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
                .contains("会话级技术面试控制器")
                .contains("不是给“上一题”写评语")
                .contains("滚动规划下一步动作")
                .contains("面试早期应优先建立粗颗粒画像")
                .contains("面试中期应在少数高价值主线上做必要确认")
                .contains("面试后期应优先填补画像缺口")
                .contains("StrategyCode")
                .contains("只能从当前注入的策略池中选择一个 `finalDecision`")
                .contains("`expectedPoints` 只是当前题的参考锚点")
                .contains("请求提示、请求澄清")
                .contains("先做会话判断，再去策略池中映射动作")
                .contains("规划先于策略")
                .contains("特殊话语优先处理")
                .contains("answerUnderstanding")
                .contains("planningIntent")
                .contains("nextFocus")
                .contains("当下一题不是项目题时")
                .contains("共享变量线程安全")
                .contains("StringBuilder与StringBuffer线程安全差异")
                .contains("联合索引设计原则")
                .contains("@Async事务失效机制")
                .contains("nextProjectPoint")
                .contains("nextItemType")
                .contains("nextItemName")
                .contains("difficultyHint")
                .contains("`queryText`")
                .contains("独立")
                .contains("完整")
                .contains("自然语言")
                .contains("`keywordHints`")
                .contains("术语锚点")
                .contains("相邻一级")
                .contains("`targetDomainCode` 必须从【主考纲剩余待考察域（菜单）】中选择一个合法的 `domainCode`")
                .contains("绝不能写成完整问句")
                .contains("绝不允许把下一题准备问的知识点提前预支写进去")
                .doesNotContain("goal")
                .doesNotContain("displayQuery")
                .doesNotContain("mustHaveClues")
                .doesNotContain("avoidClues")
                .doesNotContain("技术钩子")
                .doesNotContain("candidateStrategies")
                .doesNotContain("expectedAnswerPoints")
                .doesNotContain("nextQuestionType")
                .doesNotContain("nextEntryAction")
                .doesNotContain("DOMAIN_")
                .doesNotContain("primaryQuery")
                .doesNotContain("alternateQueries")
                .doesNotContain("retrievalType")
                .doesNotContain("expectedEvidence")
                .doesNotContain("avoidEvidence")
                .doesNotContain("软约束")
                .doesNotContain("回答评价");
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
        assertThat(rendered.getSystemPrompt())
                .contains("efficiency")
                .contains("guiding")
                .contains("stress")
                .contains("首题默认零前缀")
                .contains("不应默认写成“好，先……”")
                .contains("先简短介绍一下你的技术背景和最近项目。")
                .doesNotContain("好，先简短介绍一下你的技术背景和最近项目。");
    }

    @Test
    @DisplayName("question_consult template should load and render")
    void renderQuestionConsult_shouldLoad() {
        RenderedPrompt rendered = service.render("question_consult", "v1", Map.ofEntries(
                Map.entry("positionCode", "FRONTEND"),
                Map.entry("experienceLevel", "JUNIOR"),
                Map.entry("mode", "practice"),
                Map.entry("questionStem", "请解释浏览器渲染流水线。"),
                Map.entry("questionType", "PRINCIPLE"),
                Map.entry("domainCode", "browser_runtime"),
                Map.entry("domainName", "浏览器运行时"),
                Map.entry("originalAnswerText", "我会从 parse、layout、paint 三段来讲。"),
                Map.entry("evaluationScore", "86"),
                Map.entry("evaluationCommentary", "主线是对的，但边界条件还不够。"),
                Map.entry("strengthPoints", "- 主流程完整"),
                Map.entry("weakPoints", "- 缺少性能边界"),
                Map.entry("idealAnswerOutline", "- 定义\n- 流程\n- 优化"),
                Map.entry("rewrittenAnswer", "参考答案"),
                Map.entry("consultHistory", """
- user: 为什么这题失分？
- assistant: 主要失分在边界条件和验证步骤没有展开。
"""),
                Map.entry("latestUserQuestion", "那我该怎么重答？")
        ));

        assertThat(rendered.getPromptCode()).isEqualTo("question_consult");
        assertThat(rendered.getUserPrompt())
                .contains("请解释浏览器渲染流水线")
                .contains("为什么这题失分")
                .contains("那我该怎么重答");
        assertThat(rendered.getSystemPrompt())
                .contains("单题复盘教练")
                .contains("不要编造候选人没说过的经历")
                .contains("不要脱离当前题目");
    }
}
