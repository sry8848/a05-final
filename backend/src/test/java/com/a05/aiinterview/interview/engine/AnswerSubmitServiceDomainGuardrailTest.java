package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.resume.mapper.ResumeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerSubmitServiceDomainGuardrailTest {

    @Test
    void submitAnswer_shouldRewriteHardFailNextDomainUsingSyllabusOrderFallback() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient, sessionMapper, questionMapper, attemptMapper, resumeMapper,
                persistenceService, reportService, new ObjectMapper()
        );

        InterviewSession session = baseSession();
        InterviewQuestion question = baseQuestion();

        when(attemptMapper.selectByAttemptId("attempt-hard-fail")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人不会，切换到其他知识域验证基本盘。")
                                .answerVerdict("WEAK")
                                .decision("broaden")
                                .targetFocus("缓存击穿")
                                .targetAngle("implementation")
                                .difficultyAdjustment("same")
                                .nextQuestionGoal("切到下一个关键域验证缓存击穿")
                                .nextDomainId(6L)
                                .nextDomainCode("redis")
                                .nextDomainName("Redis")
                                .questionType("PRINCIPLE")
                                .focusPoint("缓存击穿")
                                .domainOutcome("circuit_broken")
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(1L)
                        .attemptId("attempt-hard-fail")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(11L);
        request.setAttemptId("attempt-hard-fail");
        request.setAnswerText("不会");
        request.setIsFinal(true);

        service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();
        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("java_core");
        assertThat(persisted.getQuestionType()).isEqualTo("PRINCIPLE");
    }

    @Test
    void submitAnswer_shouldKeepAiRescueQuestionTypeAndGoalWhileOnlyCorrectingSameDomain() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient, sessionMapper, questionMapper, attemptMapper, resumeMapper,
                persistenceService, reportService, new ObjectMapper()
        );

        InterviewSession session = baseSession();
        InterviewQuestion question = baseQuestion();

        when(attemptMapper.selectByAttemptId("attempt-retry")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人有印象但不扎实，降阶补问。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("RabbitMQ")
                                .targetAngle("implementation")
                                .difficultyAdjustment("up")
                                .nextQuestionGoal("降阶补问并验证 RabbitMQ 的基础理解")
                                .nextDomainId(8L)
                                .nextDomainCode("mq")
                                .nextDomainName("消息队列")
                                .questionType("PROJECT_DEEP_DIVE")
                                .focusPoint("RabbitMQ")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(2L)
                        .attemptId("attempt-retry")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("rescue")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(11L);
        request.setAttemptId("attempt-retry");
        request.setAnswerText("有点忘了，知道大概思路");
        request.setIsFinal(true);

        service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();
        assertThat(persisted.getDecision()).isEqualTo("rescue");
        assertThat(persisted.getNextDomainCode()).isEqualTo("redis");
        assertThat(persisted.getNextDomainId()).isEqualTo(6L);
        assertThat(persisted.getNextDomainName()).isEqualTo("Redis");
        assertThat(persisted.getQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(persisted.getTargetFocus()).isEqualTo("RabbitMQ");
        assertThat(persisted.getFocusPoint()).isEqualTo("RabbitMQ");
        assertThat(persisted.getNextQuestionGoal()).isEqualTo("降阶补问并验证 RabbitMQ 的基础理解");
        assertThat(persisted.getDifficultyAdjustment()).isEqualTo("same");
    }

    @Test
    void submitAnswer_shouldNotThrowWhenRescueRequestsUpgradeWithinSameDomain() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient, sessionMapper, questionMapper, attemptMapper, resumeMapper,
                persistenceService, reportService, new ObjectMapper()
        );

        InterviewSession session = baseSessionWithRedisTargetDepth("L3");
        InterviewQuestion question = baseQuestion();

        when(attemptMapper.selectByAttemptId("attempt-rescue-up")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人答得不扎实，需要补救。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("缓存击穿")
                                .targetAngle("implementation")
                                .difficultyAdjustment("up")
                                .nextQuestionGoal("补问缓存击穿基础理解")
                                .nextDomainId(6L)
                                .nextDomainCode("redis")
                                .nextDomainName("Redis")
                                .questionType("PRINCIPLE")
                                .focusPoint("缓存击穿")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(3L)
                        .attemptId("attempt-rescue-up")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("rescue")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(11L);
        request.setAttemptId("attempt-rescue-up");
        request.setAnswerText("我不太确定");
        request.setIsFinal(true);

        assertThatCode(() -> service.submitAnswer(1L, 2L, request))
                .doesNotThrowAnyException();
    }

    @Test
    void submitAnswer_shouldBroadenWhenDomainAlreadyUsedRescueOnce() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient, sessionMapper, questionMapper, attemptMapper, resumeMapper,
                persistenceService, reportService, new ObjectMapper()
        );

        InterviewSession session = baseSessionWithRescueCounters(Map.of("redis", 1), 1, null, 0);
        InterviewQuestion question = baseQuestion();

        when(attemptMapper.selectByAttemptId("attempt-domain-rescue-limit")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人答得不扎实，需要补救。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("缓存击穿")
                                .targetAngle("implementation")
                                .difficultyAdjustment("down")
                                .nextQuestionGoal("补问缓存击穿基础理解")
                                .nextDomainId(6L)
                                .nextDomainCode("redis")
                                .nextDomainName("Redis")
                                .questionType("PRINCIPLE")
                                .focusPoint("缓存击穿")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(4L)
                        .attemptId("attempt-domain-rescue-limit")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(11L);
        request.setAttemptId("attempt-domain-rescue-limit");
        request.setAnswerText("不知道");
        request.setIsFinal(true);

        service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();
        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("java_core");
    }

    @Test
    void submitAnswer_shouldBroadenWhenSessionRescueLimitReached() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient, sessionMapper, questionMapper, attemptMapper, resumeMapper,
                persistenceService, reportService, new ObjectMapper()
        );

        InterviewSession session = baseSessionWithRescueCounters(Map.of("java_core", 1, "mq", 2), 3, null, 0);
        InterviewQuestion question = baseQuestion();

        when(attemptMapper.selectByAttemptId("attempt-session-rescue-limit")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人答得不扎实，需要补救。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("缓存击穿")
                                .targetAngle("implementation")
                                .difficultyAdjustment("down")
                                .nextQuestionGoal("补问缓存击穿基础理解")
                                .nextDomainId(6L)
                                .nextDomainCode("redis")
                                .nextDomainName("Redis")
                                .questionType("PRINCIPLE")
                                .focusPoint("缓存击穿")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(5L)
                        .attemptId("attempt-session-rescue-limit")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(11L);
        request.setAttemptId("attempt-session-rescue-limit");
        request.setAnswerText("不知道");
        request.setIsFinal(true);

        service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();
        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("java_core");
    }

    @Test
    void submitAnswer_shouldStopSameFocusFollowupAfterFiveTurns() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ResumeMapper resumeMapper = mock(ResumeMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);

        AnswerSubmitService service = new AnswerSubmitService(
                aiClient, sessionMapper, questionMapper, attemptMapper, resumeMapper,
                persistenceService, reportService, new ObjectMapper()
        );

        InterviewSession session = baseSessionWithRescueCounters(Map.of(), 0, "缓存击穿", 5);
        InterviewQuestion question = baseQuestion();

        when(attemptMapper.selectByAttemptId("attempt-focus-limit")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(11L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人回答较强，可以继续深挖。")
                                .answerVerdict("STRONG")
                                .decision("followup")
                                .targetFocus("缓存击穿")
                                .targetAngle("implementation")
                                .difficultyAdjustment("up")
                                .nextQuestionGoal("继续深挖缓存击穿")
                                .nextDomainId(6L)
                                .nextDomainCode("redis")
                                .nextDomainName("Redis")
                                .questionType("PRINCIPLE")
                                .focusPoint("缓存击穿")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(6L)
                        .attemptId("attempt-focus-limit")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(11L);
        request.setAttemptId("attempt-focus-limit");
        request.setAnswerText("我会从布隆过滤器、互斥锁和永不过期方案几个角度说明。");
        request.setIsFinal(true);

        service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();
        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("java_core");
    }

    private InterviewSession baseSession() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(2L);
        session.setStatus("in_progress");
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setMode("practice");
        session.setCurrentQuestionNo(2);
        session.setContextWindowSize(5);
        session.setStateLedgerJson(Map.of(
                "domain_states", List.of(
                        Map.of("domain_id", "redis", "status", "IN_PROGRESS", "target_depth", "L2", "current_depth", "", "saturated", false, "evidence_refs", List.of()),
                        Map.of("domain_id", "java_core", "status", "UNASKED", "target_depth", "L2", "current_depth", "", "saturated", false, "evidence_refs", List.of()),
                        Map.of("domain_id", "mq", "status", "COVERED", "target_depth", "L3", "current_depth", "L2", "saturated", true, "evidence_refs", List.of(5L))
                ),
                "question_mix_progress", Map.of("INTRO", 1, "PRINCIPLE", 0),
                "asked_total", 1
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainId", 6L, "domainCode", "redis", "domainName", "Redis", "targetDepth", "L2", "priority", "high", "focusPoints", List.of("缓存击穿")),
                        Map.of("domainId", 1L, "domainCode", "java_core", "domainName", "Java 核心", "targetDepth", "L2", "priority", "high", "focusPoints", List.of("集合框架")),
                        Map.of("domainId", 8L, "domainCode", "mq", "domainName", "消息队列", "targetDepth", "L3", "priority", "high", "focusPoints", List.of("RabbitMQ 延迟消息"))
                )
        ));
        return session;
    }

    private InterviewSession baseSessionWithRedisTargetDepth(String redisTargetDepth) {
        InterviewSession session = baseSession();
        session.setStateLedgerJson(Map.of(
                "domain_states", List.of(
                        Map.of("domain_id", "redis", "status", "IN_PROGRESS", "target_depth", redisTargetDepth, "current_depth", "L2", "saturated", false, "evidence_refs", List.of()),
                        Map.of("domain_id", "java_core", "status", "UNASKED", "target_depth", "L2", "current_depth", "", "saturated", false, "evidence_refs", List.of()),
                        Map.of("domain_id", "mq", "status", "UNASKED", "target_depth", "L3", "current_depth", "", "saturated", false, "evidence_refs", List.of())
                ),
                "question_mix_progress", Map.of("INTRO", 1, "PRINCIPLE", 0),
                "asked_total", 1
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainId", 6L, "domainCode", "redis", "domainName", "Redis", "targetDepth", redisTargetDepth, "priority", "high", "focusPoints", List.of("缓存击穿")),
                        Map.of("domainId", 1L, "domainCode", "java_core", "domainName", "Java 核心", "targetDepth", "L2", "priority", "high", "focusPoints", List.of("集合框架")),
                        Map.of("domainId", 8L, "domainCode", "mq", "domainName", "消息队列", "targetDepth", "L3", "priority", "high", "focusPoints", List.of("RabbitMQ 延迟消息"))
                )
        ));
        return session;
    }

    private InterviewSession baseSessionWithRescueCounters(Map<String, Integer> rescueCountsByDomain,
                                                           int rescueTotal,
                                                           String lastFocusPoint,
                                                           int focusStreak) {
        InterviewSession session = baseSession();
        session.setStateLedgerJson(new java.util.LinkedHashMap<>(Map.of(
                "domain_states", List.of(
                        Map.of("domain_id", "redis", "status", "IN_PROGRESS", "target_depth", "L3", "current_depth", "L2", "saturated", false, "evidence_refs", List.of()),
                        Map.of("domain_id", "java_core", "status", "UNASKED", "target_depth", "L2", "current_depth", "", "saturated", false, "evidence_refs", List.of()),
                        Map.of("domain_id", "mq", "status", "UNASKED", "target_depth", "L3", "current_depth", "", "saturated", false, "evidence_refs", List.of())
                ),
                "question_mix_progress", Map.of("INTRO", 1, "PRINCIPLE", 0),
                "asked_total", 1,
                "rescue_total", rescueTotal,
                "rescue_counts_by_domain", rescueCountsByDomain,
                "current_focus_streak", focusStreak
        )));
        session.getStateLedgerJson().put("last_focus_point", lastFocusPoint);
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainId", 6L, "domainCode", "redis", "domainName", "Redis", "targetDepth", "L3", "priority", "high", "focusPoints", List.of("缓存击穿")),
                        Map.of("domainId", 1L, "domainCode", "java_core", "domainName", "Java 核心", "targetDepth", "L2", "priority", "high", "focusPoints", List.of("集合框架")),
                        Map.of("domainId", 8L, "domainCode", "mq", "domainName", "消息队列", "targetDepth", "L3", "priority", "high", "focusPoints", List.of("RabbitMQ 延迟消息"))
                )
        ));
        return session;
    }

    private InterviewQuestion baseQuestion() {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(11L);
        question.setSessionId(1L);
        question.setQuestionType("PRINCIPLE");
        question.setDomainId(6L);
        question.setTargetDepth("L2");
        question.setStem("请解释缓存击穿。");
        question.setExpectedPoints(List.of("说明定义", "说明方案"));
        question.setGenerationContextJson(Map.of("domainCode", "redis"));
        return question;
    }
}
