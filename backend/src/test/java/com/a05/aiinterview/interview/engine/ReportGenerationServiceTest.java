package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.ReportGenerationInput;
import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewReport;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewReportMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportGenerationServiceTest {

    @Test
    void generateAsync_shouldPersistProfessionalRadarScores() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        ReportGenerationService service = new ReportGenerationService(
                aiClient, sessionMapper, questionMapper, attemptMapper, reportMapper
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setMode("professional");
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("SENIOR");
        session.setTitle("Java 后端模拟面试");
        session.setSyllabusJson(Map.of("domains", List.of(
                Map.of("domainCode", "java_core", "domainName", "Java 核心基础")
        )));
        session.setStateLedgerJson(Map.of("asked_total", 3));
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(reportMapper.selectBySessionId(1L)).thenReturn(null);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(11L);
        question.setSessionId(1L);
        question.setQuestionNo(1);
        question.setQuestionType("PRINCIPLE");
        question.setTargetDepth("L3");
        question.setStem("请讲讲 HashMap");
        question.setExpectedPoints(List.of("扩容", "冲突"));
        question.setGenerationContextJson(Map.of("domainCode", "java_core"));
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setQuestionId(11L);
        attempt.setAnswerText("回答内容");
        attempt.setIsFinal(true);
        attempt.setCreatedAt(LocalDateTime.now());
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));

        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(88))
                .summary("总结")
                .strengths(List.of("优势"))
                .weaknesses(List.of("短板"))
                .improvementSuggestions(List.of("建议"))
                .skillDomainScores(List.of(
                        ReportGenerationOutput.SkillDomainScore.builder()
                                .domainCode("java_core")
                                .domainName("Java 核心基础")
                                .score(BigDecimal.valueOf(86))
                                .achievedDepth("L3")
                                .commentary("掌握较好")
                                .build()
                ))
                .comprehensiveRadarScores(List.of(
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("fundamentals")
                                .dimensionName("基础原理掌握")
                                .score(BigDecimal.valueOf(90))
                                .build()
                ))
                .build();
        when(aiClient.callReportGeneration(any())).thenReturn(
                AiCallResult.<ReportGenerationOutput>builder().output(output).build()
        );

        service.generateAsync(1L);

        ArgumentCaptor<ReportGenerationInput> inputCaptor = ArgumentCaptor.forClass(ReportGenerationInput.class);
        verify(aiClient).callReportGeneration(inputCaptor.capture());
        assertEquals("professional", inputCaptor.getValue().getMode());

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        InterviewReport persisted = reportCaptor.getValue();
        assertNotNull(persisted.getComprehensiveRadarScores());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) persisted.getComprehensiveRadarScores().get("dimensions");
        assertEquals(1, dimensions.size());
        assertEquals("fundamentals", dimensions.get(0).get("dimensionKey"));
    }

    @Test
    void generateAsync_shouldKeepPracticeRadarScoresNull() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        ReportGenerationService service = new ReportGenerationService(
                aiClient, sessionMapper, questionMapper, attemptMapper, reportMapper
        );

        InterviewSession session = new InterviewSession();
        session.setId(2L);
        session.setMode("practice");
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("JUNIOR");
        session.setTitle("练习模式");
        when(sessionMapper.selectById(2L)).thenReturn(session);
        when(reportMapper.selectBySessionId(2L)).thenReturn(null);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(attemptMapper.selectList(any())).thenReturn(List.of());

        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(70))
                .summary("总结")
                .strengths(List.of())
                .weaknesses(List.of())
                .improvementSuggestions(List.of())
                .skillDomainScores(List.of())
                .comprehensiveRadarScores(null)
                .build();
        when(aiClient.callReportGeneration(any())).thenReturn(
                AiCallResult.<ReportGenerationOutput>builder().output(output).build()
        );

        service.generateAsync(2L);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        assertNull(reportCaptor.getValue().getComprehensiveRadarScores());
    }
}
