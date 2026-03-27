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
import com.a05.aiinterview.interview.service.InterviewSessionStatusService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
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
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ReportGenerationService service = new ReportGenerationService(
                aiClient, sessionMapper, questionMapper, attemptMapper, reportMapper, statusService
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setMode("professional");
        session.setPositionCode("JAVA_BACKEND");
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
                .overallScore(BigDecimal.valueOf(11))
                .summary("总结")
                .strengths(List.of("优势"))
                .weaknesses(List.of("短板"))
                .improvementSuggestions(List.of("建议"))
                .skillDomainScores(List.of(
                        ReportGenerationOutput.SkillDomainScore.builder()
                                .domainCode("java_core")
                                .domainName("Java 核心基础")
                                .score(BigDecimal.valueOf(86))
                                .commentary("掌握较好")
                                .build()
                ))
                .comprehensiveRadarScores(List.of(
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("fundamentals")
                                .dimensionName("基础原理掌握")
                                .score(BigDecimal.valueOf(90))
                                .build()
                        ,
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("engineering_practice")
                                .dimensionName("工程实践与项目落地")
                                .score(BigDecimal.valueOf(80))
                                .build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("scenario_tradeoff")
                                .dimensionName("场景分析与方案取舍")
                                .score(BigDecimal.valueOf(70))
                                .build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("debugging")
                                .dimensionName("问题定位与排查思路")
                                .score(BigDecimal.valueOf(60))
                                .build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("communication")
                                .dimensionName("沟通表达与结构化呈现")
                                .score(BigDecimal.valueOf(50))
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
        assertEquals(BigDecimal.valueOf(72.0), persisted.getOverallScore());
        assertNotNull(persisted.getComprehensiveRadarScores());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) persisted.getComprehensiveRadarScores().get("dimensions");
        assertEquals(5, dimensions.size());
        assertEquals("fundamentals", dimensions.get(0).get("dimensionKey"));
    }

    @Test
    void generateAsync_shouldPersistPracticeRadarForInternalOverallScoreCalculation() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ReportGenerationService service = new ReportGenerationService(
                aiClient, sessionMapper, questionMapper, attemptMapper, reportMapper, statusService
        );

        InterviewSession session = new InterviewSession();
        session.setId(2L);
        session.setMode("practice");
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("JUNIOR");
        session.setTitle("练习模式");
        when(sessionMapper.selectById(2L)).thenReturn(session);
        when(reportMapper.selectBySessionId(2L)).thenReturn(null);
        when(questionMapper.selectList(any())).thenReturn(List.of());
        when(attemptMapper.selectList(any())).thenReturn(List.of());

        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(20))
                .summary("总结")
                .strengths(List.of())
                .weaknesses(List.of())
                .improvementSuggestions(List.of())
                .skillDomainScores(List.of())
                .comprehensiveRadarScores(List.of(
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("fundamentals").dimensionName("基础原理掌握").score(BigDecimal.valueOf(80)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("engineering_practice").dimensionName("工程实践与项目落地").score(BigDecimal.valueOf(70)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("scenario_tradeoff").dimensionName("场景分析与方案取舍").score(BigDecimal.valueOf(60)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("debugging").dimensionName("问题定位与排查思路").score(BigDecimal.valueOf(90)).build(),
                        ReportGenerationOutput.ComprehensiveRadarScore.builder()
                                .dimensionKey("communication").dimensionName("沟通表达与结构化呈现").score(BigDecimal.valueOf(100)).build()
                ))
                .build();
        when(aiClient.callReportGeneration(any())).thenReturn(
                AiCallResult.<ReportGenerationOutput>builder().output(output).build()
        );

        service.generateAsync(2L);

        ArgumentCaptor<InterviewReport> reportCaptor = ArgumentCaptor.forClass(InterviewReport.class);
        verify(reportMapper).insert(reportCaptor.capture());
        assertNotNull(reportCaptor.getValue().getComprehensiveRadarScores());
        assertEquals(BigDecimal.valueOf(79.0), reportCaptor.getValue().getOverallScore());
    }

    @Test
    void generateAsync_shouldStripFallbackSystemDiagnosticsFromQaPairs() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ReportGenerationService service = new ReportGenerationService(
                aiClient, sessionMapper, questionMapper, attemptMapper, reportMapper, statusService
        );

        InterviewSession session = new InterviewSession();
        session.setId(3L);
        session.setMode("practice");
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("JUNIOR");
        when(sessionMapper.selectById(3L)).thenReturn(session);
        when(reportMapper.selectBySessionId(3L)).thenReturn(null);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(21L);
        question.setQuestionNo(1);
        question.setQuestionType("BEHAVIORAL");
        question.setStem("请分享一次真实决策经历。");
        question.setGenerationContextJson(Map.of("domainCode", "intro"));
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setQuestionId(21L);
        attempt.setAnswerText("我当时先调研，再拍板。");
        attempt.setIsFinal(true);
        attempt.setCreatedAt(LocalDateTime.now());
        attempt.setEvaluationJson(Map.of(
                "effectiveDecisionSource", "SYSTEM_FALLBACK",
                "answerAssessment", "伪造评语",
                "decisionRepairAudit", Map.of("error", "bad json"),
                "terminationReason", "SYSTEM_DECISION_ERROR"
        ));
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));

        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(70))
                .summary("ok")
                .strengths(List.of())
                .weaknesses(List.of())
                .improvementSuggestions(List.of())
                .skillDomainScores(List.of())
                .build();
        when(aiClient.callReportGeneration(any())).thenReturn(AiCallResult.<ReportGenerationOutput>builder().output(output).build());

        service.generateAsync(3L);

        ArgumentCaptor<ReportGenerationInput> inputCaptor = ArgumentCaptor.forClass(ReportGenerationInput.class);
        verify(aiClient).callReportGeneration(inputCaptor.capture());
        assertThat(inputCaptor.getValue().getQuestionAnswerPairs()).hasSize(1);
        assertThat(inputCaptor.getValue().getQuestionAnswerPairs().getFirst().getAnswerText()).isEqualTo("我当时先调研，再拍板。");
    }

    @Test
    void generateAsync_shouldKeepBehavioralQuestionTypeWithoutForgingBehavioralDomainCode() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        InterviewReportMapper reportMapper = mock(InterviewReportMapper.class);
        InterviewSessionStatusService statusService = mock(InterviewSessionStatusService.class);
        ReportGenerationService service = new ReportGenerationService(
                aiClient, sessionMapper, questionMapper, attemptMapper, reportMapper, statusService
        );

        InterviewSession session = new InterviewSession();
        session.setId(4L);
        session.setMode("practice");
        session.setPositionCode("JAVA_BACKEND");
        session.setExperienceLevel("JUNIOR");
        when(sessionMapper.selectById(4L)).thenReturn(session);
        when(reportMapper.selectBySessionId(4L)).thenReturn(null);

        InterviewQuestion question = new InterviewQuestion();
        question.setId(41L);
        question.setQuestionNo(1);
        question.setQuestionType("BEHAVIORAL");
        question.setStem("请分享一次你推动协作达成结果的经历。");
        question.setGenerationContextJson(Map.of());
        when(questionMapper.selectList(any())).thenReturn(List.of(question));

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setQuestionId(41L);
        attempt.setAnswerText("我先统一目标，再拆分行动项。");
        attempt.setIsFinal(true);
        attempt.setCreatedAt(LocalDateTime.now());
        when(attemptMapper.selectList(any())).thenReturn(List.of(attempt));

        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(75))
                .summary("ok")
                .strengths(List.of())
                .weaknesses(List.of())
                .improvementSuggestions(List.of())
                .skillDomainScores(List.of())
                .build();
        when(aiClient.callReportGeneration(any())).thenReturn(
                AiCallResult.<ReportGenerationOutput>builder().output(output).build()
        );

        service.generateAsync(4L);

        ArgumentCaptor<ReportGenerationInput> inputCaptor = ArgumentCaptor.forClass(ReportGenerationInput.class);
        verify(aiClient).callReportGeneration(inputCaptor.capture());
        assertThat(inputCaptor.getValue().getQuestionAnswerPairs()).singleElement().satisfies(pair -> {
            assertThat(pair.getQuestionType()).isEqualTo("BEHAVIORAL");
            assertThat(pair.getDomainCode()).isBlank();
            assertThat(pair.getDomainName()).isEqualTo("行为题");
        });
    }
}
