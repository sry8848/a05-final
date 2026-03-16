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
                                .passCurrentLevel(false)
                                .deepen(false)
                                .signal("RETRY_SAME_DOMAIN")
                                .nextStrategy(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                                        .nextDomainId(0L)
                                        .nextDomainCode("intro")
                                        .nextDomainName("intro")
                                        .questionType("INTRO")
                                        .targetDepth("L1")
                                        .difficulty("L1")
                                        .targetSkill("引导候选人补充具体的项目经验和使用的技术栈")
                                        .focusPoint("项目经验")
                                        .expectedPoints(List.of(
                                                "说明一个做过的项目名称",
                                                "说明项目的业务目标和技术栈",
                                                "说明你在项目中的具体职责"
                                        ))
                                        .build())
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(10L)
                        .attemptId("attempt-intro-retry-1")
                        .isFinal(true)
                        .shouldEnd(false)
                        .evaluationSignal("RETRY_SAME_DOMAIN")
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

        assertThat(persisted.getSignal()).isEqualTo("RETRY_SAME_DOMAIN");
        assertThat(persisted.getNextStrategy()).isNotNull();
        assertThat(persisted.getNextStrategy().getNextDomainCode()).isEqualTo("intro");
        assertThat(persisted.getNextStrategy().getQuestionType()).isEqualTo("INTRO");
        assertThat(persisted.getNextStrategy().getTargetDepth()).isEqualTo("L1");
        assertThat(persisted.getNextStrategy().getDifficulty()).isEqualTo("L1");
        assertThat(persisted.getNextStrategy().getTargetSkill()).contains("引导候选人");
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
                                .passCurrentLevel(false)
                                .deepen(false)
                                .signal("END")
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(11L)
                        .attemptId("attempt-intro-next-2")
                        .isFinal(true)
                        .shouldEnd(false)
                        .evaluationSignal("NEXT_DOMAIN")
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

        assertThat(persisted.getSignal()).isEqualTo("NEXT_DOMAIN");
        assertThat(persisted.getNextStrategy()).isNotNull();
        assertThat(persisted.getNextStrategy().getNextDomainCode()).isEqualTo("redis");
        assertThat(persisted.getNextStrategy().getTargetDepth()).isEqualTo("L1");
        assertThat(persisted.getNextStrategy().getDifficulty()).isEqualTo("L1");
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
                                .passCurrentLevel(true)
                                .deepen(false)
                                .signal("NEXT_DOMAIN")
                                .nextStrategy(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                                        .nextDomainId(8L)
                                        .nextDomainCode("mq")
                                        .nextDomainName("消息队列")
                                        .questionType("PROJECT_DEEP_DIVE")
                                        .targetDepth("L3")
                                        .difficulty("L3")
                                        .targetSkill("RabbitMQ 延迟消息落地细节")
                                        .focusPoint("RabbitMQ 延迟消息")
                                        .expectedPoints(List.of(
                                                "说明延迟消息的实现方式",
                                                "说明业务场景中的取舍"
                                        ))
                                        .build())
                                .build())
                        .build()
        );
        when(fixture.persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(12L)
                        .attemptId("attempt-intro-junior")
                        .isFinal(true)
                        .shouldEnd(false)
                        .evaluationSignal("NEXT_DOMAIN")
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

        assertThat(persisted.getSignal()).isEqualTo("NEXT_DOMAIN");
        assertThat(persisted.getNextStrategy()).isNotNull();
        assertThat(persisted.getNextStrategy().getNextDomainCode()).isEqualTo("mq");
        assertThat(persisted.getNextStrategy().getQuestionType()).isEqualTo("PROJECT_DEEP_DIVE");
        assertThat(persisted.getNextStrategy().getTargetDepth()).isEqualTo("L2");
        assertThat(persisted.getNextStrategy().getDifficulty()).isEqualTo("L2");
        assertThat(persisted.getNextStrategy().getTargetSkill()).contains("RabbitMQ");
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
