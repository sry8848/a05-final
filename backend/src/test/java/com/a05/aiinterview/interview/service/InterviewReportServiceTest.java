package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.interview.dto.InterviewReportDto;
import com.a05.aiinterview.interview.engine.ReportGenerationService;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewReportServiceTest {

    @Test
    void getReport_shouldBuildQuestionSummariesWithLatestFinalRules() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ReportGenerationService generationService = mock(ReportGenerationService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewReportService service = new InterviewReportService(
                sessionMapper, reportMapper, questionMapper, attemptMapper, generationService, statusService
        );

        Long sessionId = 2001L;
        Long userId = 3001L;
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setMode("professional");
        session.setTargetRole("JAVA_BACKEND");
        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(statusService.resolveAndSync(session)).thenReturn("completed");

        InterviewReport report = new InterviewReport();
        report.setId(1L);
        report.setSessionId(sessionId);
        report.setOverallScore(BigDecimal.valueOf(78.5));
        report.setComprehensiveRadarScores(Map.of(
                "dimensions", List.of(
                        Map.of("dimensionKey", "fundamentals", "dimensionName", "基础原理掌握", "score", 82),
                        Map.of("dimensionKey", "communication", "dimensionName", "沟通表达与结构化呈现", "score", 75)
                )
        ));
        when(reportMapper.selectBySessionId(sessionId)).thenReturn(report);

        InterviewQuestion q1 = buildQuestion(11L, sessionId, 1, "Q1");
        InterviewQuestion q2 = buildQuestion(12L, sessionId, 2, "Q2");
        InterviewQuestion q3 = buildQuestion(13L, sessionId, 3, "Q3");
        when(questionMapper.selectList(any())).thenReturn(List.of(q1, q2, q3));

        LocalDateTime now = LocalDateTime.now();
        InterviewAttempt q1OldFinal = buildAttempt(
                101L, sessionId, 11L, true, "old answer", now.minusMinutes(2), Map.of("score", 66));
        q1OldFinal.setDetailEvaluationStatus("ready");
        InterviewAttempt q1NewerFinal = buildAttempt(
                102L, sessionId, 11L, true, "newer answer", now.minusMinutes(1), Map.of("score", 88.5));
        q1NewerFinal.setDetailEvaluationStatus("ready");
        q1NewerFinal.setDetailEvaluationJson(Map.of("score", 88.5, "commentary", "较完整"));
        InterviewAttempt q1SameTimeHigherId = buildAttempt(
                103L, sessionId, 11L, true, "latest answer", now.minusMinutes(1), Map.of("score", "89"));
        q1SameTimeHigherId.setDetailEvaluationStatus("ready");
        q1SameTimeHigherId.setDetailEvaluationJson(Map.of("score", "89"));
        InterviewAttempt q1NonFinal = buildAttempt(
                104L, sessionId, 11L, false, "draft", now, Map.of("score", 99));

        InterviewAttempt q2Skip = buildAttempt(
                201L, sessionId, 12L, true, "[skip]", now.minusSeconds(30), Map.of("score", 50));
        q2Skip.setDetailEvaluationStatus("ready");
        q2Skip.setDetailEvaluationJson(Map.of("score", 50, "commentary", "建议补强"));

        when(attemptMapper.selectBySessionId(sessionId)).thenReturn(
                List.of(q1OldFinal, q1NewerFinal, q1SameTimeHigherId, q1NonFinal, q2Skip)
        );

        InterviewReportDto dto = service.getReport(sessionId, userId);

        assertNotNull(dto);
        assertEquals("professional", dto.getMode());
        assertEquals("JAVA_BACKEND", dto.getTargetRole());
        assertNotNull(dto.getComprehensiveRadarScores());
        assertEquals(2, dto.getComprehensiveRadarScores().size());
        assertEquals("fundamentals", dto.getComprehensiveRadarScores().get(0).getDimensionKey());
        assertEquals(3, dto.getQuestions().size());

        InterviewReportDto.QuestionSummaryDto s1 = dto.getQuestions().get(0);
        assertEquals(11L, s1.getQuestionId());
        assertEquals("answered", s1.getStatus());
        // same createdAt 时选 id 更大的 final；score 为字符串，按规则返回 null
        assertNull(s1.getScore());

        InterviewReportDto.QuestionSummaryDto s2 = dto.getQuestions().get(1);
        assertEquals(12L, s2.getQuestionId());
        assertEquals("skipped", s2.getStatus());
        assertEquals(BigDecimal.valueOf(50), s2.getScore());

        InterviewReportDto.QuestionSummaryDto s3 = dto.getQuestions().get(2);
        assertEquals(13L, s3.getQuestionId());
        assertEquals("pending", s3.getStatus());
        assertNull(s3.getScore());
    }

    @Test
    void getReport_shouldReturnGeneratingWhenDetailEvaluationStillRunning() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ReportGenerationService generationService = mock(ReportGenerationService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewReportService service = new InterviewReportService(
                sessionMapper, reportMapper, questionMapper, attemptMapper, generationService, statusService
        );

        Long sessionId = 3001L;
        Long userId = 4001L;
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("report_generating");
        session.setMode("professional");
        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(statusService.resolveAndSync(session)).thenReturn("report_generating");

        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setOverallScore(BigDecimal.valueOf(88));
        when(reportMapper.selectBySessionId(sessionId)).thenReturn(report);

        InterviewQuestion question = buildQuestion(31L, sessionId, 1, "Q1");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        InterviewAttempt attempt = buildAttempt(
                301L, sessionId, 31L, true, "answer", LocalDateTime.now(), Map.of("score", 88));
        attempt.setDetailEvaluationStatus("generating");
        when(attemptMapper.selectBySessionId(sessionId)).thenReturn(List.of(attempt));

        InterviewReportDto dto = service.getReport(sessionId, userId);

        assertEquals("generating", dto.getReportStatus());
        assertNull(dto.getQuestions());
    }

    @Test
    void getReport_shouldReturnFailedWhenAnyDetailEvaluationFailed() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ReportGenerationService generationService = mock(ReportGenerationService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewReportService service = new InterviewReportService(
                sessionMapper, reportMapper, questionMapper, attemptMapper, generationService, statusService
        );

        Long sessionId = 5001L;
        Long userId = 6001L;
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("aborted");
        session.setMode("practice");
        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(statusService.resolveAndSync(session)).thenReturn("aborted");

        InterviewReport report = new InterviewReport();
        report.setSessionId(sessionId);
        report.setOverallScore(BigDecimal.valueOf(70));
        when(reportMapper.selectBySessionId(sessionId)).thenReturn(report);

        InterviewQuestion question = buildQuestion(51L, sessionId, 1, "Q1");
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        InterviewAttempt attempt = buildAttempt(
                501L, sessionId, 51L, true, "answer", LocalDateTime.now(), Map.of("score", 70));
        attempt.setDetailEvaluationStatus("failed");
        when(attemptMapper.selectBySessionId(sessionId)).thenReturn(List.of(attempt));

        InterviewReportDto dto = service.getReport(sessionId, userId);

        assertEquals("failed", dto.getReportStatus());
        assertNull(dto.getQuestions());
    }

    @Test
    void getReport_shouldReturnFailedWhenSessionAlreadyAbortedAndReportMissing() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        ReportGenerationService generationService = mock(ReportGenerationService.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        InterviewReportService service = new InterviewReportService(
                sessionMapper, reportMapper, questionMapper, attemptMapper, generationService, statusService
        );

        Long sessionId = 7001L;
        Long userId = 8001L;
        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus("aborted");
        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(reportMapper.selectBySessionId(sessionId)).thenReturn(null);
        when(statusService.resolveAndSync(session)).thenReturn("aborted");

        InterviewReportDto dto = service.getReport(sessionId, userId);

        assertEquals("failed", dto.getReportStatus());
    }

    private InterviewQuestion buildQuestion(Long id, Long sessionId, int no, String stem) {
        InterviewQuestion q = new InterviewQuestion();
        q.setId(id);
        q.setSessionId(sessionId);
        q.setQuestionNo(no);
        q.setStem(stem);
        return q;
    }

    private InterviewAttempt buildAttempt(
            Long id,
            Long sessionId,
            Long questionId,
            boolean isFinal,
            String answerText,
            LocalDateTime createdAt,
            Map<String, Object> evaluationJson) {
        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setId(id);
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setIsFinal(isFinal);
        attempt.setAnswerText(answerText);
        attempt.setCreatedAt(createdAt);
        attempt.setEvaluationJson(evaluationJson);
        return attempt;
    }
}
