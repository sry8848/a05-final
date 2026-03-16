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

class AnswerSubmitServiceDomainGuardrailTest {

    @Test
    void submitAnswer_shouldRewriteHardFailNextDomainAwayFromCurrentAndCoveredDomains() {
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
                                .passCurrentLevel(false)
                                .deepen(false)
                                .signal("NEXT_DOMAIN")
                                .nextStrategy(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                                        .nextDomainId(6L)
                                        .nextDomainCode("redis")
                                        .nextDomainName("Redis")
                                        .questionType("PRINCIPLE")
                                        .targetDepth("L2")
                                        .difficulty("L2")
                                        .targetSkill("缓存击穿")
                                        .expectedPoints(List.of("说明缓存击穿"))
                                        .focusPoint("缓存击穿")
                                        .build())
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(1L)
                        .attemptId("attempt-hard-fail")
                        .isFinal(true)
                        .shouldEnd(false)
                        .evaluationSignal("NEXT_DOMAIN")
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
        assertThat(persisted.getSignal()).isEqualTo("NEXT_DOMAIN");
        assertThat(persisted.getNextStrategy()).isNotNull();
        assertThat(persisted.getNextStrategy().getNextDomainCode()).isEqualTo("java_core");
    }

    @Test
    void submitAnswer_shouldKeepRetrySameDomainAtSameDepth() {
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
                                .passCurrentLevel(false)
                                .deepen(false)
                                .signal("RETRY_SAME_DOMAIN")
                                .nextStrategy(EvaluationDecisionOutput.NextQuestionStrategy.builder()
                                        .nextDomainId(8L)
                                        .nextDomainCode("mq")
                                        .nextDomainName("消息队列")
                                        .questionType("PROJECT_DEEP_DIVE")
                                        .targetDepth("L3")
                                        .difficulty("L3")
                                        .targetSkill("RabbitMQ 延迟消息")
                                        .expectedPoints(List.of("说明延迟消息"))
                                        .focusPoint("RabbitMQ")
                                        .build())
                                .build())
                        .build()
        );
        when(persistenceService.persist(any(), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptDbId(2L)
                        .attemptId("attempt-retry")
                        .isFinal(true)
                        .shouldEnd(false)
                        .evaluationSignal("RETRY_SAME_DOMAIN")
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
        assertThat(persisted.getSignal()).isEqualTo("RETRY_SAME_DOMAIN");
        assertThat(persisted.getNextStrategy()).isNotNull();
        assertThat(persisted.getNextStrategy().getNextDomainCode()).isEqualTo("redis");
        assertThat(persisted.getNextStrategy().getTargetDepth()).isEqualTo("L2");
        assertThat(persisted.getNextStrategy().getDifficulty()).isEqualTo("L2");
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
