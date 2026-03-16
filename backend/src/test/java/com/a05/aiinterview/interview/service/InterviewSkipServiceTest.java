package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.SkipAndNextRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.engine.AnswerSubmitPersistenceService;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.ReportGenerationService;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterviewSkipServiceTest {

    @Test
    void skipAndNext_shouldReturnIdempotentResponseWhenAttemptExists() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        InterviewSkipService service = new InterviewSkipService(
                sessionMapper, questionMapper, attemptMapper, persistenceService, reportGenerationService
        );

        InterviewAttempt existing = new InterviewAttempt();
        existing.setAttemptId("attempt-1");
        existing.setEvaluationJson(Map.of("signal", "END"));
        when(attemptMapper.selectByAttemptId("attempt-1")).thenReturn(existing);

        SkipAndNextRequest request = new SkipAndNextRequest();
        request.setAttemptId("attempt-1");

        SubmitAttemptResponse response = service.skipAndNext(1L, 2L, 3L, request);
        assertEquals("END", response.getEvaluationSignal());
        assertNull(response.getStreamAttemptId());
        assertEquals("report_generating", response.getSessionStatus());
        verify(persistenceService, never()).persist(any(), any(), any(), any());
    }

    @Test
    void skipAndNext_shouldPersistSkipAndReturnNextDomain() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        InterviewSkipService service = new InterviewSkipService(
                sessionMapper, questionMapper, attemptMapper, persistenceService, reportGenerationService
        );

        when(attemptMapper.selectByAttemptId("attempt-2")).thenReturn(null);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(3L);
        session.setStatus("in_progress");
        session.setCurrentQuestionNo(3);
        session.setStateLedgerJson(Map.of(
                "max_questions", 10,
                "domain_states", List.of(
                        Map.of("domain_id", "redis", "status", "IN_PROGRESS"),
                        Map.of("domain_id", "java_core", "status", "UNASKED"),
                        Map.of("domain_id", "mq", "status", "COVERED")
                )
        ));
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of("domainId", 1L, "domainCode", "java_core", "domainName", "Java 核心"),
                        Map.of("domainId", 8L, "domainCode", "mq", "domainName", "消息队列")
                )
        ));
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        question.setDomainId(6L);
        question.setTargetDepth("L2");
        question.setTargetSkill("并发控制");
        question.setGenerationContextJson(Map.of("domainCode", "redis"));
        when(questionMapper.selectById(2L)).thenReturn(question);

        SkipAndNextRequest request = new SkipAndNextRequest();
        request.setAttemptId("attempt-2");

        SubmitAttemptResponse response = service.skipAndNext(1L, 2L, 3L, request);
        assertEquals("NEXT_DOMAIN", response.getEvaluationSignal());
        assertEquals("attempt-2", response.getStreamAttemptId());
        assertEquals("in_progress", response.getSessionStatus());

        ArgumentCaptor<SubmitAttemptRequest> submitCaptor = ArgumentCaptor.forClass(SubmitAttemptRequest.class);
        ArgumentCaptor<EvaluationDecisionOutput> evalCaptor = ArgumentCaptor.forClass(EvaluationDecisionOutput.class);
        verify(persistenceService).persist(eq(1L), eq(question), submitCaptor.capture(), evalCaptor.capture());
        assertEquals("attempt-2", submitCaptor.getValue().getAttemptId());
        assertEquals("[skip]", submitCaptor.getValue().getAnswerText());
        assertEquals(true, submitCaptor.getValue().getIsFinal());
        assertEquals("java_core", evalCaptor.getValue().getNextStrategy().getNextDomainCode());
    }

    @Test
    void skipAndNext_shouldEndWhenReachMaxQuestions() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        InterviewSkipService service = new InterviewSkipService(
                sessionMapper, questionMapper, attemptMapper, persistenceService, reportGenerationService
        );

        when(attemptMapper.selectByAttemptId("attempt-3")).thenReturn(null);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(3L);
        session.setStatus("in_progress");
        session.setCurrentQuestionNo(10);
        session.setStateLedgerJson(Map.of("max_questions", 10));
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        when(questionMapper.selectById(2L)).thenReturn(question);

        SkipAndNextRequest request = new SkipAndNextRequest();
        request.setAttemptId("attempt-3");

        SubmitAttemptResponse response = service.skipAndNext(1L, 2L, 3L, request);
        assertEquals("END", response.getEvaluationSignal());
        assertEquals("report_generating", response.getSessionStatus());
        verify(reportGenerationService).generateAsync(1L);
    }
}
