package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionDetailEvaluationServiceTest {

    @Test
    void evaluateByAttemptId_shouldKeepPendingAndSkipAiForNonFinal() {
        AiClient aiClient = mock(AiClient.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        QuestionDetailEvaluationService service = new QuestionDetailEvaluationService(
                aiClient, attemptMapper, questionMapper, sessionMapper, new ObjectMapper(), statusService
        );

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(100L);
        attempt.setIsFinal(false);
        attempt.setDetailEvaluationStatus("generating");
        when(attemptMapper.selectById(100L)).thenReturn(attempt);
        when(attemptMapper.updateById(any())).thenReturn(1);

        service.evaluateByAttemptId(100L);

        ArgumentCaptor<InterviewAttempt> captor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper).updateById(captor.capture());
        assertEquals("pending", captor.getValue().getDetailEvaluationStatus());
        verify(aiClient, never()).callQuestionDetailEvaluation(any());
    }

    @Test
    void evaluateByAttemptId_shouldSkipWhenAlreadyReady() {
        AiClient aiClient = mock(AiClient.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        QuestionDetailEvaluationService service = new QuestionDetailEvaluationService(
                aiClient, attemptMapper, questionMapper, sessionMapper, new ObjectMapper(), statusService
        );

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(100L);
        attempt.setIsFinal(true);
        attempt.setDetailEvaluationStatus("ready");
        when(attemptMapper.selectById(100L)).thenReturn(attempt);

        service.evaluateByAttemptId(100L);

        verify(attemptMapper, never()).updateById(any());
        verify(aiClient, never()).callQuestionDetailEvaluation(any());
    }

    @Test
    void evaluateByAttemptId_shouldSetGeneratingThenReadyOnSuccess() {
        AiClient aiClient = mock(AiClient.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        QuestionDetailEvaluationService service = new QuestionDetailEvaluationService(
                aiClient, attemptMapper, questionMapper, sessionMapper, new ObjectMapper(), statusService
        );

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(100L);
        attempt.setSessionId(1L);
        attempt.setQuestionId(2L);
        attempt.setAttemptId("attempt-1");
        attempt.setAnswerText("answer");
        attempt.setIsFinal(true);
        attempt.setDetailEvaluationStatus("pending");
        when(attemptMapper.selectById(100L)).thenReturn(attempt);
        when(attemptMapper.updateById(any())).thenReturn(1);
        when(attemptMapper.selectBySessionId(1L)).thenReturn(List.of(attempt));

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        session.setContextWindowSize(5);
        session.setSyllabusJson(Map.of("domains", List.of(
                Map.of("domainCode", "java_concurrency", "domainName", "Java 并发")
        )));
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        question.setStem("请解释AQS");
        question.setQuestionType("PRINCIPLE");
        question.setExpectedPoints(List.of("state", "CAS"));
        question.setGenerationContextJson(Map.of("domainCode", "java_concurrency"));
        when(questionMapper.selectById(2L)).thenReturn(question);
        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(question));

        QuestionDetailEvaluationOutput output = QuestionDetailEvaluationOutput.builder()
                .score(BigDecimal.valueOf(82))
                .commentary("ok")
                .strengthPoints(List.of("s1"))
                .weakPoints(List.of("w1"))
                .highlightedSegments(List.of(
                        QuestionDetailEvaluationOutput.HighlightedSegment.builder()
                                .segment("主要提升了系统性能")
                                .label("strength")
                                .comment("建议补充具体指标")
                                .build()
                ))
                .idealAnswerOutline(List.of("a", "b", "c"))
                .rewrittenAnswer("rewritten")
                .build();
        when(aiClient.callQuestionDetailEvaluation(any())).thenReturn(
                AiCallResult.<QuestionDetailEvaluationOutput>builder().output(output).build()
        );

        service.evaluateByAttemptId(100L);

        ArgumentCaptor<InterviewAttempt> updateCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper, times(2)).updateById(updateCaptor.capture());
        assertEquals("generating", updateCaptor.getAllValues().get(0).getDetailEvaluationStatus());
        assertEquals("ready", updateCaptor.getAllValues().get(1).getDetailEvaluationStatus());
        Map<String, Object> readyJson = updateCaptor.getAllValues().get(1).getDetailEvaluationJson();
        org.assertj.core.api.Assertions.assertThat(readyJson.keySet()).containsExactlyInAnyOrder(
                "score",
                "commentary",
                "strengthPoints",
                "weakPoints",
                "evaluatedDomains",
                "highlightedSegments",
                "idealAnswerOutline",
                "rewrittenAnswer"
        );
        verify(aiClient).callQuestionDetailEvaluation(any());
        verify(statusService).resolveAndSync(1L);
    }

    @Test
    void evaluateByAttemptId_shouldClampQuestionDetailScoresToPercentageRange() {
        AiClient aiClient = mock(AiClient.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        QuestionDetailEvaluationService service = new QuestionDetailEvaluationService(
                aiClient, attemptMapper, questionMapper, sessionMapper, objectMapper, statusService
        );

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(100L);
        attempt.setSessionId(1L);
        attempt.setQuestionId(2L);
        attempt.setAttemptId("attempt-1");
        attempt.setAnswerText("answer");
        attempt.setIsFinal(true);
        attempt.setDetailEvaluationStatus("pending");
        when(attemptMapper.selectById(100L)).thenReturn(attempt);
        when(attemptMapper.updateById(any())).thenReturn(1);
        when(attemptMapper.selectBySessionId(1L)).thenReturn(List.of(attempt));

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        session.setContextWindowSize(5);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        question.setStem("请解释AQS");
        question.setQuestionType("PRINCIPLE");
        question.setGenerationContextJson(Map.of("domainCode", "java_concurrency"));
        when(questionMapper.selectById(2L)).thenReturn(question);
        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(question));

        QuestionDetailEvaluationOutput output = QuestionDetailEvaluationOutput.builder()
                .score(new BigDecimal("120.5"))
                .evaluatedDomains(List.of(
                        QuestionDetailEvaluationOutput.EvaluatedDomain.builder()
                                .domainCode("java_concurrency")
                                .domainName("Java 并发")
                                .score(new BigDecimal("-3"))
                                .commentary("需要加强")
                                .build(),
                        QuestionDetailEvaluationOutput.EvaluatedDomain.builder()
                                .domainCode("jvm")
                                .domainName("JVM")
                                .score(new BigDecimal("105"))
                                .commentary("略超范围")
                                .build()
                ))
                .build();
        when(aiClient.callQuestionDetailEvaluation(any())).thenReturn(
                AiCallResult.<QuestionDetailEvaluationOutput>builder().output(output).build()
        );

        service.evaluateByAttemptId(100L);

        ArgumentCaptor<InterviewAttempt> updateCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper, times(2)).updateById(updateCaptor.capture());
        Map<String, Object> readyJson = updateCaptor.getAllValues().get(1).getDetailEvaluationJson();
        QuestionDetailEvaluationOutput saved = objectMapper.convertValue(readyJson, QuestionDetailEvaluationOutput.class);
        assertEquals(new BigDecimal("100"), saved.getScore());
        assertEquals(new BigDecimal("0"), saved.getEvaluatedDomains().get(0).getScore());
        assertEquals(new BigDecimal("100"), saved.getEvaluatedDomains().get(1).getScore());
    }

    @Test
    void evaluateByAttemptId_shouldSetFailedWhenAiThrows() {
        AiClient aiClient = mock(AiClient.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        QuestionDetailEvaluationService service = new QuestionDetailEvaluationService(
                aiClient, attemptMapper, questionMapper, sessionMapper, new ObjectMapper(), statusService
        );

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(100L);
        attempt.setSessionId(1L);
        attempt.setQuestionId(2L);
        attempt.setAttemptId("attempt-1");
        attempt.setAnswerText("answer");
        attempt.setIsFinal(true);
        attempt.setDetailEvaluationStatus("pending");
        when(attemptMapper.selectById(100L)).thenReturn(attempt);
        when(attemptMapper.updateById(any())).thenReturn(1);
        when(attemptMapper.selectBySessionId(1L)).thenReturn(List.of(attempt));

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setMode("professional");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(2L);
        question.setSessionId(1L);
        question.setStem("Q");
        question.setQuestionType("PRINCIPLE");
        question.setGenerationContextJson(Map.of("domainCode", "java_concurrency"));
        when(questionMapper.selectById(2L)).thenReturn(question);
        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(question));

        when(aiClient.callQuestionDetailEvaluation(any())).thenThrow(new RuntimeException("boom"));

        service.evaluateByAttemptId(100L);

        ArgumentCaptor<InterviewAttempt> updateCaptor = ArgumentCaptor.forClass(InterviewAttempt.class);
        verify(attemptMapper, times(2)).updateById(updateCaptor.capture());
        assertEquals("generating", updateCaptor.getAllValues().get(0).getDetailEvaluationStatus());
        assertEquals("failed", updateCaptor.getAllValues().get(1).getDetailEvaluationStatus());
        verify(statusService).resolveAndSync(1L);
    }
}
