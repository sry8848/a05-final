package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AnswerSubmitService evaluation input tests")
class AnswerSubmitServiceEvaluationInputTest {

    @Test
    @DisplayName("buildCurrentQuestionContext should include domainCode for principle question")
    void buildCurrentQuestionContext_shouldIncludeDomainCodeForPrincipleQuestion() throws Exception {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(65L);
        session.setPositionCode("JAVA_BACKEND");
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "spring",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("Seata AT 模式边界")
                        )
                )
        ));
        session.setStateLedgerJson(Map.of());

        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setQuestionType("PRINCIPLE");
        question.setStem("请解释 Seata AT 的边界。");
        question.setFocusPoint("Seata AT 模式边界");
        question.setGenerationContextJson(Map.of(
                "domainCode", "spring",
                "focusPoint", "Seata AT模式边界"
        ));

        EvaluationDecisionInput.CurrentQuestionContext currentQuestion =
                ReflectionTestUtils.invokeMethod(service, "buildCurrentQuestionContext", session, question);

        assertThat(currentQuestion.getQuestionType()).isEqualTo("PRINCIPLE");
        assertThat(currentQuestion.getDomainCode()).isEqualTo("spring");
        assertThat(currentQuestion.getDomainName()).isEqualTo("Spring 框架");
        assertThat(currentQuestion.getCurrentFocus()).isEqualTo("Seata AT模式边界");
        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = new ObjectMapper().convertValue(currentQuestion, Map.class);
        assertThat(serialized).containsKeys("domainCode", "domainName", "currentFocus");
    }

    @Test
    @DisplayName("remaining domain menu builder should only keep uncovered domains")
    void remainingDomainMenuBuilder_shouldOnlyKeepUncoveredDomains() {
        RemainingDomainMenuBuilder builder = new RemainingDomainMenuBuilder();

        InterviewSession session = new InterviewSession();
        session.setStateLedgerJson(Map.of(
                "domain_states", List.of(
                        Map.of("domainCode", "spring", "status", "COVERED"),
                        Map.of("domainCode", "redis", "status", "UNASKED")
                )
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "spring",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("事务传播")
                        ),
                        Map.of(
                                "domainCode", "redis",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        )
                )
        ));

        List<EvaluationDecisionInput.RemainingTargetDomain> remaining = builder.build(session);

        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getDomainCode()).isEqualTo("redis");
        assertThat(remaining.getFirst().getDomainName()).isEqualTo("Redis 缓存");
    }

    @Test
    @DisplayName("buildEvaluationInput should source remaining domains and strategies from dedicated builders")
    void buildEvaluationInput_shouldSourceRemainingDomainsAndStrategiesFromDedicatedBuilders() {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(77L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setStateLedgerJson(Map.of(
                "quota_state", QuotaStateSupport.initialQuotaState(),
                "max_questions", 14,
                "domain_states", List.of(
                        Map.of("domainCode", "spring", "status", "COVERED"),
                        Map.of("domainCode", "redis", "status", "UNASKED")
                )
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "spring",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("事务传播")
                        ),
                        Map.of(
                                "domainCode", "redis",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        )
                ),
                "experienceItems", List.of(
                        Map.of(
                                "itemType", "PROJECT",
                                "itemName", "Chabst",
                                "resumeDescription", "项目描述",
                                "techHooks", List.of("Redis")
                        )
                )
        ));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setId(900L);
        currentQuestion.setStem("请解释缓存击穿。");
        currentQuestion.setExpectedPoints(List.of("定义"));
        currentQuestion.setGenerationContextJson(Map.of(
                "domainCode", "redis",
                "focusPoint", "缓存击穿"
        ));

        EvaluationDecisionInput input = ReflectionTestUtils.invokeMethod(
                service,
                "buildEvaluationInput",
                session,
                currentQuestion,
                List.of(currentQuestion),
                List.of(),
                "回答"
        );

        assertThat(input.getRemainingTargetDomains())
                .extracting(EvaluationDecisionInput.RemainingTargetDomain::getDomainCode)
                .containsExactly("redis");
        assertThat(input.getAvailableStrategies())
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .contains(
                        StrategyCode.S_P_VERIFY.code(),
                        StrategyCode.S_ENTER_PROJECT.code(),
                        StrategyCode.S_ENTER_SCENARIO.code(),
                        StrategyCode.S_WRAPUP.code()
                );
    }

    @Test
    @DisplayName("buildEvaluationInput should expose progress fields and backfill max questions from experience profile")
    void buildEvaluationInput_shouldExposeProgressFieldsAndBackfillMaxQuestionsFromExperienceProfile() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper()),
                new RemainingDomainMenuBuilder(),
                new AvailableStrategyAssembler(),
                new DecisionExecutionPlanBuilder(),
                new DecisionRepairOrchestrator(aiClient, new DecisionExecutionPlanBuilder()),
                new SystemFallbackPlanBuilder(),
                new PlannerHistoryBuilderService(sessionMapper, questionMapper)
        );

        InterviewSession session = new InterviewSession();
        session.setId(88L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("JUNIOR");
        session.setCurrentQuestionNo(4);
        session.setStateLedgerJson(new java.util.LinkedHashMap<>(Map.of(
                "quota_state", new java.util.LinkedHashMap<>(Map.of(
                        QuotaStateSupport.SAME_POINT_CONTINUE, 1,
                        QuotaStateSupport.PROJECT_TOTAL, 2
                )),
                "domain_states", List.of(
                        Map.of("domainCode", "redis", "status", "UNASKED")
                )
        )));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "redis",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        )
                ),
                "experienceItems", List.of(
                        Map.of(
                                "itemType", "PROJECT",
                                "itemName", "Chabst",
                                "resumeDescription", "项目描述",
                                "techHooks", List.of("Redis")
                        )
                )
        ));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(901L);
        currentQuestion.setQuestionNo(6);
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setStem("请解释缓存击穿。");
        currentQuestion.setExpectedPoints(List.of("定义"));
        currentQuestion.setGenerationContextJson(Map.of(
                "domainCode", "redis",
                "focusPoint", "缓存击穿"
        ));

        EvaluationDecisionInput input = ReflectionTestUtils.invokeMethod(
                service,
                "buildEvaluationInput",
                session,
                currentQuestion,
                List.of(currentQuestion),
                List.of(),
                "回答"
        );

        assertThat(input.getQuestionIndex()).isEqualTo(6);
        assertThat(input.getMaxQuestions()).isEqualTo(15);
        assertThat(input.getQuotaSnapshot()).containsKeys(
                QuotaStateSupport.SAME_POINT_CONTINUE,
                QuotaStateSupport.PROJECT_TOTAL
        );
        assertThat(input.getQuotaSnapshot().get(QuotaStateSupport.SAME_POINT_CONTINUE).getUsed()).isEqualTo(1);
        assertThat(input.getQuotaSnapshot().get(QuotaStateSupport.SAME_POINT_CONTINUE).getMax()).isEqualTo(2);
        assertThat(input.getQuotaSnapshot().get(QuotaStateSupport.PROJECT_TOTAL).getUsed()).isEqualTo(2);
        assertThat(input.getQuotaSnapshot().get(QuotaStateSupport.PROJECT_TOTAL).getMax()).isEqualTo(8);
        assertThat(session.getStateLedgerJson()).containsEntry("max_questions", 15);

        verify(sessionMapper).updateById(argThat(updated ->
                updated != null
                        && updated.getId().equals(88L)
                        && updated.getStateLedgerJson() != null
                        && Integer.valueOf(15).equals(updated.getStateLedgerJson().get("max_questions"))
        ));
    }

    @Test
    @DisplayName("buildRecentInterviewMemory should keep latest three history answers raw and older ones summarized")
    void buildRecentInterviewMemory_shouldKeepLatestThreeHistoryAnswersRawAndOlderOnesSummarized() throws Exception {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(98L);
        session.setStateLedgerJson(Map.of());

        InterviewQuestion q1 = buildHistoryQuestion(1L, 1, "Q1");
        InterviewQuestion q2 = buildHistoryQuestion(2L, 2, "Q2");
        InterviewQuestion q3 = buildHistoryQuestion(3L, 3, "Q3");
        InterviewQuestion q4 = buildHistoryQuestion(4L, 4, "Q4");
        InterviewQuestion currentQuestion = buildHistoryQuestion(5L, 5, "Q5-current");

        InterviewAttempt a1 = buildHistoryAttempt(1L, "第1题回答，应该被摘要保留。");
        InterviewAttempt a2 = buildHistoryAttempt(2L, "第2题回答，应该被摘要保留。");
        InterviewAttempt a3 = buildHistoryAttempt(3L, "第3题回答，应该作为原始内容保留。");
        InterviewAttempt a4 = buildHistoryAttempt(4L, "第4题回答，应该作为原始内容保留。");
        InterviewAttempt currentAttempt = buildHistoryAttempt(5L, "当前题回答，不应进入历史。");

        List<EvaluationDecisionInput.RecentInterviewMemoryItem> memory = ReflectionTestUtils.invokeMethod(
                service,
                "buildRecentInterviewMemory",
                session,
                currentQuestion,
                List.of(q1, q2, q3, q4, currentQuestion),
                List.of(a1, a2, a3, a4, currentAttempt)
        );

        assertThat(memory).hasSize(4);
        assertThat(memory).extracting(EvaluationDecisionInput.RecentInterviewMemoryItem::getQuestionStem)
                .containsExactly("Q1", "Q2", "Q3", "Q4");
        assertThat(memory).extracting(EvaluationDecisionInput.RecentInterviewMemoryItem::getAnswerContentType)
                .containsExactly("SUMMARY", "RAW", "RAW", "RAW");
        assertThat(memory.getFirst().getAnswerContent()).isEqualTo("第1题回答，应该被摘要保留。");
        assertThat(memory.get(1).getAnswerContent()).isEqualTo("第2题回答，应该被摘要保留。");
        assertThat(memory.get(2).getAnswerContent()).isEqualTo("第3题回答，应该作为原始内容保留。");
        assertThat(memory.get(3).getAnswerContent()).isEqualTo("第4题回答，应该作为原始内容保留。");
        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = new ObjectMapper().convertValue(memory.getFirst(), Map.class);
        assertThat(serialized).containsKeys("domainCode", "domainName", "focusPoint", "answerContent", "answerContentType")
                .doesNotContainKeys("answerSummary", "answerAssessment");
    }

    @Test
    @DisplayName("buildRecentInterviewMemory should keep all earlier history after recent raw window")
    void buildRecentInterviewMemory_shouldKeepAllEarlierHistoryAfterRecentRawWindow() {
        AnswerSubmitService service = buildService();

        InterviewSession session = new InterviewSession();
        session.setId(99L);
        session.setStateLedgerJson(Map.of());

        InterviewQuestion currentQuestion = buildHistoryQuestion(8L, 8, "Q8-current");
        List<InterviewQuestion> historyQuestions = List.of(
                buildHistoryQuestion(1L, 1, "Q1"),
                buildHistoryQuestion(2L, 2, "Q2"),
                buildHistoryQuestion(3L, 3, "Q3"),
                buildHistoryQuestion(4L, 4, "Q4"),
                buildHistoryQuestion(5L, 5, "Q5"),
                buildHistoryQuestion(6L, 6, "Q6"),
                buildHistoryQuestion(7L, 7, "Q7"),
                currentQuestion
        );
        List<InterviewAttempt> attempts = List.of(
                buildHistoryAttempt(1L, "A1"),
                buildHistoryAttempt(2L, "A2"),
                buildHistoryAttempt(3L, "A3"),
                buildHistoryAttempt(4L, "A4"),
                buildHistoryAttempt(5L, "A5"),
                buildHistoryAttempt(6L, "A6"),
                buildHistoryAttempt(7L, "A7"),
                buildHistoryAttempt(8L, "A8")
        );

        List<EvaluationDecisionInput.RecentInterviewMemoryItem> memory = ReflectionTestUtils.invokeMethod(
                service,
                "buildRecentInterviewMemory",
                session,
                currentQuestion,
                historyQuestions,
                attempts
        );

        assertThat(memory).hasSize(7);
        assertThat(memory).extracting(EvaluationDecisionInput.RecentInterviewMemoryItem::getAnswerContentType)
                .containsExactly("SUMMARY", "SUMMARY", "SUMMARY", "SUMMARY", "RAW", "RAW", "RAW");
    }

    @Test
    @DisplayName("buildEvaluationInput should include cross-session blocked knowledge points and project entry points")
    void buildEvaluationInput_shouldIncludeCrossSessionBlockedData() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        PlannerHistoryBuilderService plannerHistoryBuilderService =
                new PlannerHistoryBuilderService(sessionMapper, questionMapper);
        AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper()),
                new RemainingDomainMenuBuilder(),
                new AvailableStrategyAssembler(),
                new DecisionExecutionPlanBuilder(),
                new DecisionRepairOrchestrator(aiClient, new DecisionExecutionPlanBuilder()),
                new SystemFallbackPlanBuilder(),
                plannerHistoryBuilderService
        );

        InterviewSession session = new InterviewSession();
        session.setId(120L);
        session.setUserId(1L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setStateLedgerJson(Map.of(
                "quota_state", QuotaStateSupport.initialQuotaState(),
                "max_questions", 14,
                "domain_states", List.of(Map.of("domainCode", "spring", "status", "UNASKED"))
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "spring",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("事务传播")
                        )
                ),
                "experienceItems", List.of(
                        Map.of(
                                "itemType", "PROJECT",
                                "itemName", "Chabst",
                                "resumeDescription", "项目描述",
                                "techHooks", List.of("RabbitMQ 延迟消息处理超时订单", "Redisson 秒杀锁")
                        )
                )
        ));

        InterviewSession knowledgeHistory = new InterviewSession();
        knowledgeHistory.setId(119L);
        knowledgeHistory.setFinishedAt(java.time.LocalDateTime.of(2026, 3, 24, 10, 0));
        knowledgeHistory.setStateLedgerJson(Map.of(
                "covered_points", List.of("Redis / 缓存击穿", "MySQL / 索引优化")
        ));

        InterviewSession projectHistory = new InterviewSession();
        projectHistory.setId(118L);
        projectHistory.setFinishedAt(java.time.LocalDateTime.of(2026, 3, 23, 10, 0));
        projectHistory.setStateLedgerJson(Map.of());

        InterviewQuestion projectQuestion = new InterviewQuestion();
        projectQuestion.setId(7001L);
        projectQuestion.setSessionId(118L);
        projectQuestion.setQuestionNo(2);
        projectQuestion.setQuestionType("PROJECT_DEEP_DIVE");
        projectQuestion.setGenerationContextJson(Map.of(
                "activeItemType", "PROJECT",
                "activeItemName", "Chabst",
                "projectPoint", "RabbitMQ 延迟消息处理超时订单"
        ));

        when(sessionMapper.selectPlannerRecentSessions(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("JAVA_BACKEND"),
                org.mockito.ArgumentMatchers.isNull(),
                any(),
                org.mockito.ArgumentMatchers.eq(120L),
                org.mockito.ArgumentMatchers.eq(3)
        )).thenReturn(List.of(knowledgeHistory));
        when(sessionMapper.selectPlannerRecentSessions(
                1L,
                "JAVA_BACKEND",
                null,
                null,
                120L,
                2
        )).thenReturn(List.of(projectHistory));
        when(questionMapper.selectList(any())).thenReturn(List.of(projectQuestion));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setId(901L);
        currentQuestion.setStem("请解释缓存击穿。");
        currentQuestion.setExpectedPoints(List.of("定义"));
        currentQuestion.setGenerationContextJson(Map.of(
                "domainCode", "spring",
                "focusPoint", "缓存击穿"
        ));

        EvaluationDecisionInput input = ReflectionTestUtils.invokeMethod(
                service,
                "buildEvaluationInput",
                session,
                currentQuestion,
                List.of(currentQuestion),
                List.<InterviewAttempt>of(),
                "回答"
        );

        assertThat(input.getCrossSessionBlockedKnowledgePoints())
                .containsExactly("Redis / 缓存击穿", "MySQL / 索引优化");
        assertThat(input.getProjectAndInternshipSummary()).hasSize(1);
        assertThat(input.getProjectAndInternshipSummary().getFirst().getBlockedEntryPoints())
                .containsExactly("RabbitMQ 延迟消息处理超时订单");
    }

    @Test
    @DisplayName("buildEvaluationInput should keep all syllabus projects and only block matched project entry points")
    void buildEvaluationInput_shouldKeepAllProjectsAndOnlyBlockMatchedProjectEntryPoints() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        PlannerHistoryBuilderService plannerHistoryBuilderService =
                new PlannerHistoryBuilderService(sessionMapper, questionMapper);
        AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper()),
                new RemainingDomainMenuBuilder(),
                new AvailableStrategyAssembler(),
                new DecisionExecutionPlanBuilder(),
                new DecisionRepairOrchestrator(aiClient, new DecisionExecutionPlanBuilder()),
                new SystemFallbackPlanBuilder(),
                plannerHistoryBuilderService
        );

        InterviewSession session = new InterviewSession();
        session.setId(130L);
        session.setUserId(1L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setStateLedgerJson(Map.of(
                "quota_state", QuotaStateSupport.initialQuotaState(),
                "max_questions", 14,
                "domain_states", List.of(Map.of("domainCode", "spring", "status", "UNASKED"))
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "spring",
                                "domainName", "Spring 框架",
                                "focusPoints", List.of("事务传播")
                        )
                ),
                "experienceItems", List.of(
                        Map.of(
                                "itemType", "PROJECT",
                                "itemName", "AI 模拟面试系统",
                                "resumeDescription", "项目描述A",
                                "techHooks", List.of("Redis缓存多轮对话上下文", "流式出题状态管理")
                        ),
                        Map.of(
                                "itemType", "PROJECT",
                                "itemName", "苍穹外卖（企业级餐饮外卖平台）",
                                "resumeDescription", "项目描述B",
                                "techHooks", List.of("JWT 令牌实现双端登录鉴权")
                        )
                )
        ));

        InterviewSession projectHistory = new InterviewSession();
        projectHistory.setId(129L);
        projectHistory.setFinishedAt(java.time.LocalDateTime.of(2026, 3, 26, 10, 0));
        projectHistory.setStateLedgerJson(Map.of());

        InterviewQuestion projectQuestion = new InterviewQuestion();
        projectQuestion.setId(7101L);
        projectQuestion.setSessionId(129L);
        projectQuestion.setQuestionNo(2);
        projectQuestion.setQuestionType("PROJECT_DEEP_DIVE");
        projectQuestion.setGenerationContextJson(Map.of(
                "activeItemType", "PROJECT",
                "activeItemName", "AI 模拟面试系统",
                "projectPoint", "Redis缓存多轮对话上下文"
        ));

        when(sessionMapper.selectPlannerRecentSessions(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("JAVA_BACKEND"),
                org.mockito.ArgumentMatchers.isNull(),
                any(),
                org.mockito.ArgumentMatchers.eq(130L),
                org.mockito.ArgumentMatchers.eq(3)
        )).thenReturn(List.of());
        when(sessionMapper.selectPlannerRecentSessions(
                1L,
                "JAVA_BACKEND",
                null,
                null,
                130L,
                2
        )).thenReturn(List.of(projectHistory));
        when(questionMapper.selectList(any())).thenReturn(List.of(projectQuestion));

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("INTRO");
        currentQuestion.setId(902L);
        currentQuestion.setStem("请做自我介绍");
        currentQuestion.setExpectedPoints(List.of("项目"));
        currentQuestion.setGenerationContextJson(Map.of(
                "domainCode", "intro"
        ));

        EvaluationDecisionInput input = ReflectionTestUtils.invokeMethod(
                service,
                "buildEvaluationInput",
                session,
                currentQuestion,
                List.of(currentQuestion),
                List.<InterviewAttempt>of(),
                "回答"
        );

        assertThat(input.getProjectAndInternshipSummary())
                .extracting(EvaluationDecisionInput.ProjectAndInternshipItem::getItemName)
                .containsExactly("AI 模拟面试系统", "苍穹外卖（企业级餐饮外卖平台）");
        assertThat(input.getProjectAndInternshipSummary().get(0).getBlockedEntryPoints())
                .containsExactly("Redis缓存多轮对话上下文");
        assertThat(input.getProjectAndInternshipSummary().get(1).getBlockedEntryPoints()).isEmpty();
    }

    private AnswerSubmitService buildService() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        return new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                mock(InterviewAttemptMapper.class),
                mock(AnswerSubmitPersistenceService.class),
                mock(ReportGenerationService.class),
                new InterviewDebugTraceService(new ObjectMapper()),
                new RemainingDomainMenuBuilder(),
                new AvailableStrategyAssembler(),
                new DecisionExecutionPlanBuilder(),
                new DecisionRepairOrchestrator(aiClient, new DecisionExecutionPlanBuilder()),
                new SystemFallbackPlanBuilder(),
                new PlannerHistoryBuilderService(sessionMapper, questionMapper)
        );
    }

    private InterviewQuestion buildHistoryQuestion(Long id, int questionNo, String stem) {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(id);
        question.setQuestionNo(questionNo);
        question.setQuestionType("PRINCIPLE");
        question.setStem(stem);
        question.setGenerationContextJson(Map.of());
        return question;
    }

    private InterviewAttempt buildHistoryAttempt(Long questionId, String answerText) {
        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setQuestionId(questionId);
        attempt.setAnswerText(answerText);
        attempt.setIsFinal(true);
        attempt.setEvaluationJson(Map.of());
        return attempt;
    }
}
