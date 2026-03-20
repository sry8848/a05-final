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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerSubmitServiceIntroGuardrailTest {

    @Test
    void submitAnswer_shouldKeepFirstIntroRetryAsGuidedIntroQuestion() {
        ServiceFixture fixture = new ServiceFixture("FRESH_GRAD", 0);
        InterviewSession session = fixture.session();
        InterviewQuestion question = fixture.introQuestion(100L);

        when(fixture.attemptMapper.selectByAttemptId("attempt-intro-retry-1")).thenReturn(null);
        when(fixture.sessionMapper.selectById(1L)).thenReturn(session);
        when(fixture.questionMapper.selectById(100L)).thenReturn(question);
        when(fixture.questionMapper.selectList(any())).thenReturn(List.of(question));
        when(fixture.attemptMapper.selectList(any())).thenReturn(List.of());
        when(fixture.aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("项目信息不足，需要继续引导候选人讲清项目经历。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("项目经验")
                                .targetAngle("role")
                                .difficultyAdjustment("same")
                                .nextQuestionGoal("继续引导候选人补充项目经历")
                                .nextDomainCode("intro")
                                .nextDomainName("intro")
                                .questionType("INTRO")
                                .focusPoint("项目经验")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(10L)
                        .attemptId("attempt-intro-retry-1")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("rescue")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(100L);
        request.setAttemptId("attempt-intro-retry-1");
        request.setAnswerText("我也是人");
        request.setIsFinal(true);

        fixture.service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(fixture.persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();

        assertThat(persisted.getDecision()).isEqualTo("rescue");
        assertThat(persisted.getNextDomainCode()).isEqualTo("intro");
        assertThat(persisted.getQuestionType()).isEqualTo("INTRO");
        assertThat(persisted.getFocusPoint()).isEqualTo("项目经验");
    }

    @Test
    void submitAnswer_shouldPreserveAiIntroRescueFocusAndGoal() {
        ServiceFixture fixture = new ServiceFixture("FRESH_GRAD", 0);
        InterviewSession session = fixture.session();
        InterviewQuestion question = fixture.introQuestion(103L);

        when(fixture.attemptMapper.selectByAttemptId("attempt-intro-preserve")).thenReturn(null);
        when(fixture.sessionMapper.selectById(1L)).thenReturn(session);
        when(fixture.questionMapper.selectById(103L)).thenReturn(question);
        when(fixture.questionMapper.selectList(any())).thenReturn(List.of(question));
        when(fixture.attemptMapper.selectList(any())).thenReturn(List.of());
        when(fixture.aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人项目名和简历存在偏差，需要继续核验项目真实性。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("项目名称一致性与实操细节验证")
                                .targetAngle("role")
                                .difficultyAdjustment("down")
                                .nextQuestionGoal("请确认你提到的项目是否就是简历里的 Chabst，并给出一个你亲手做过的接口或排障细节。")
                                .nextDomainId(0L)
                                .nextDomainCode("intro")
                                .nextDomainName("intro")
                                .questionType("INTRO")
                                .focusPoint("项目真实性与职责边界")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(13L)
                        .attemptId("attempt-intro-preserve")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("rescue")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(103L);
        request.setAttemptId("attempt-intro-preserve");
        request.setAnswerText("我做的是苍穹外卖。");
        request.setIsFinal(true);

        fixture.service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(fixture.persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();

        assertThat(persisted.getDecision()).isEqualTo("rescue");
        assertThat(persisted.getQuestionType()).isEqualTo("INTRO");
        assertThat(persisted.getNextDomainCode()).isEqualTo("intro");
        assertThat(persisted.getNextDomainId()).isEqualTo(0L);
        assertThat(persisted.getTargetFocus()).isEqualTo("项目名称一致性与实操细节验证");
        assertThat(persisted.getFocusPoint()).isEqualTo("项目真实性与职责边界");
        assertThat(persisted.getNextQuestionGoal()).isEqualTo("请确认你提到的项目是否就是简历里的 Chabst，并给出一个你亲手做过的接口或排障细节。");
    }

    @Test
    void submitAnswer_shouldBroadenIntroWhenSessionRescueLimitReached() {
        ServiceFixture fixture = new ServiceFixture("FRESH_GRAD", 0);
        InterviewSession session = fixture.session();
        session.setStateLedgerJson(new LinkedHashMap<>(session.getStateLedgerJson()));
        session.getStateLedgerJson().put("rescue_total", 3);
        session.getStateLedgerJson().put("rescue_counts_by_domain", new LinkedHashMap<>(Map.of("intro", 1)));
        InterviewQuestion question = fixture.introQuestion(104L);

        when(fixture.attemptMapper.selectByAttemptId("attempt-intro-rescue-limit")).thenReturn(null);
        when(fixture.sessionMapper.selectById(1L)).thenReturn(session);
        when(fixture.questionMapper.selectById(104L)).thenReturn(question);
        when(fixture.questionMapper.selectList(any())).thenReturn(List.of(question));
        when(fixture.attemptMapper.selectList(any())).thenReturn(List.of());
        when(fixture.aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("项目信息不足，需要继续补救。")
                                .answerVerdict("PARTIAL")
                                .decision("rescue")
                                .targetFocus("项目经验")
                                .targetAngle("role")
                                .difficultyAdjustment("down")
                                .nextQuestionGoal("继续引导候选人补充项目经历")
                                .nextDomainCode("intro")
                                .nextDomainName("intro")
                                .questionType("INTRO")
                                .focusPoint("项目经验")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(14L)
                        .attemptId("attempt-intro-rescue-limit")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(104L);
        request.setAttemptId("attempt-intro-rescue-limit");
        request.setAnswerText("我项目讲不太清楚。");
        request.setIsFinal(true);

        fixture.service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(fixture.persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();

        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("redis");
    }

    @Test
    void submitAnswer_shouldForceSecondIntroToNextDomainAndCapFreshGradDepthToL1() {
        ServiceFixture fixture = new ServiceFixture("FRESH_GRAD", 1);
        InterviewSession session = fixture.session();
        InterviewQuestion question = fixture.introQuestion(101L);

        when(fixture.attemptMapper.selectByAttemptId("attempt-intro-next-2")).thenReturn(null);
        when(fixture.sessionMapper.selectById(1L)).thenReturn(session);
        when(fixture.questionMapper.selectById(101L)).thenReturn(question);
        when(fixture.questionMapper.selectList(any())).thenReturn(List.of(question));
        when(fixture.attemptMapper.selectList(any())).thenReturn(List.of());
        when(fixture.aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("继续追问收益低，准备切到正式知识域。")
                                .answerVerdict("PARTIAL")
                                .decision("wrapup")
                                .targetFocus("综合收束")
                                .targetAngle("role")
                                .difficultyAdjustment("same")
                                .nextQuestionGoal("结束当前 intro")
                                .domainOutcome("covered")
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(11L)
                        .attemptId("attempt-intro-next-2")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(101L);
        request.setAttemptId("attempt-intro-next-2");
        request.setAnswerText("还是没什么项目可讲");
        request.setIsFinal(true);

        fixture.service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(fixture.persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();

        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("redis");
        assertThat(persisted.getQuestionType()).isEqualTo("PRINCIPLE");
    }

    @Test
    void submitAnswer_shouldKeepAiChosenFormalDomainButCapExperiencedCandidateDepthToL2() {
        ServiceFixture fixture = new ServiceFixture("JUNIOR", 1);
        InterviewSession session = fixture.session();
        InterviewQuestion question = fixture.introQuestion(102L);

        when(fixture.attemptMapper.selectByAttemptId("attempt-intro-junior")).thenReturn(null);
        when(fixture.sessionMapper.selectById(1L)).thenReturn(session);
        when(fixture.questionMapper.selectById(102L)).thenReturn(question);
        when(fixture.questionMapper.selectList(any())).thenReturn(List.of(question));
        when(fixture.attemptMapper.selectList(any())).thenReturn(List.of());
        when(fixture.aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(EvaluationDecisionOutput.builder()
                                .answerAssessment("候选人项目交代较清楚，可以转到业务相关知识域。")
                                .answerVerdict("PARTIAL")
                                .decision("broaden")
                                .targetFocus("RabbitMQ 延迟消息")
                                .targetAngle("implementation")
                                .difficultyAdjustment("up")
                                .nextQuestionGoal("切到业务相关知识域继续验证")
                                .nextDomainId(8L)
                                .nextDomainCode("mq")
                                .nextDomainName("消息队列")
                                .questionType("PROJECT_DEEP_DIVE")
                                .focusPoint("RabbitMQ 延迟消息")
                                .domainOutcome("continue")
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(12L)
                        .attemptId("attempt-intro-junior")
                        .isFinal(true)
                        .shouldEnd(false)
                        .decision("broaden")
                        .build()
        );

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setQuestionId(102L);
        request.setAttemptId("attempt-intro-junior");
        request.setAnswerText("我做过订单和消息模块。");
        request.setIsFinal(true);

        fixture.service.submitAnswer(1L, 2L, request);

        ArgumentCaptor<EvaluationDecisionOutput> captor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(fixture.persistenceService).persist(any(), any(), any(), captor.capture());
        EvaluationDecisionOutput persisted = captor.getValue();

        assertThat(persisted.getDecision()).isEqualTo("broaden");
        assertThat(persisted.getNextDomainCode()).isEqualTo("mq");
        assertThat(persisted.getQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(persisted.getFocusPoint()).contains("RabbitMQ");
    }

    private static final class ServiceFixture {
        private final AiClient aiClient = mock(AiClient.class);
        private final InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        private final InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        private final InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
        private final AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        private final ReportGenerationService reportService = mock(ReportGenerationService.class);
        private final AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                resumeMapper,
                persistenceService,
                reportService,
                new ObjectMapper()
        );

        private final String experienceLevel;
        private final int introCount;

        private ServiceFixture(String experienceLevel, int introCount) {
            this.experienceLevel = experienceLevel;
            this.introCount = introCount;
        }

        private InterviewSession session() {
            InterviewSession session = new InterviewSession();
            session.setId(1L);
            session.setUserId(2L);
            session.setStatus("in_progress");
            session.setTargetRole("JAVA_BACKEND");
            session.setExperienceLevel(experienceLevel);
            session.setMode("practice");
            session.setCurrentQuestionNo(introCount + 1);
            session.setContextWindowSize(5);
            session.setStateLedgerJson(Map.of(
                    "domain_states", List.of(
                            Map.of(
                                    "domain_id", "java_core",
                                    "status", "IN_PROGRESS",
                                    "current_depth", "",
                                    "saturated", false,
                                    "evidence_refs", List.of()
                            ),
                            Map.of(
                                    "domain_id", "redis",
                                    "status", "UNASKED",
                                    "current_depth", "",
                                    "saturated", false,
                                    "evidence_refs", List.of()
                            ),
                            Map.of(
                                    "domain_id", "mq",
                                    "status", "UNASKED",
                                    "current_depth", "",
                                    "saturated", false,
                                    "evidence_refs", List.of()
                            )
                    ),
                    "question_mix_progress", Map.of("INTRO", introCount, "PROJECT_DEEP_DIVE", 0, "PRINCIPLE", 0),
                    "asked_total", introCount
            ));
            session.setSyllabusJson(Map.of(
                    "domains", List.of(
                            Map.of(
                                    "domainId", 6L,
                                    "domainCode", "redis",
                                    "domainName", "Redis 缓存",
                                    "targetDepth", "L2",
                                    "priority", "high",
                                    "focusPoints", List.of("缓存击穿")
                            ),
                            Map.of(
                                    "domainId", 1L,
                                    "domainCode", "java_core",
                                    "domainName", "Java 核心基础",
                                    "targetDepth", "L2",
                                    "priority", "high",
                                    "focusPoints", List.of("集合扩容机制")
                            ),
                            Map.of(
                                    "domainId", 8L,
                                    "domainCode", "mq",
                                    "domainName", "消息队列",
                                    "targetDepth", "L3",
                                    "priority", "high",
                                    "focusPoints", List.of("RabbitMQ 延迟消息")
                            )
                    ),
                    "questionMixPlan", Map.of("INTRO", 1, "PROJECT_DEEP_DIVE", 4, "PRINCIPLE", 7)
            ));
            return session;
        }

        private InterviewQuestion introQuestion(Long questionId) {
            InterviewQuestion question = new InterviewQuestion();
            question.setId(questionId);
            question.setSessionId(1L);
            question.setQuestionType("INTRO");
            question.setDomainId(null);
            question.setTargetDepth("L1");
            question.setStem("请做一下自我介绍。");
            question.setExpectedPoints(List.of("技术方向", "项目经历"));
            question.setGenerationContextJson(Map.of("domainCode", "intro"));
            return question;
        }
    }
}
