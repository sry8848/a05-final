package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AnswerSubmitService decision flow tests")
class AnswerSubmitServiceDecisionFlowTest {

    @Test
    @DisplayName("invalid raw decision should repair once and continue with repaired plan")
    void invalidRawDecision_shouldRepairOnceAndContinue() {
        Fixture f = fixture(baseSession(Map.of()), principleQuestion());
        when(f.aiClient.callEvaluationDecision(any()))
                .thenReturn(aiResult("""
                        {"decisionReason":"raw","interviewAction":"CONTINUE","finalDecision":"BAD","nextFocus":"缓存一致性","targetDomainCode":"","newCoveredDomains":[],"newCoveredPoints":[],"retrievalPlans":[]}
                        """))
                .thenReturn(aiResult("""
                        {"decisionReason":"repair","interviewAction":"CONTINUE","finalDecision":"S_P_VERIFY","nextFocus":"缓存一致性","targetDomainCode":"","newCoveredDomains":[],"newCoveredPoints":[],"retrievalPlans":[]}
                        """));
        when(f.persistenceService.persist(eq(1L), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptId("attempt-1")
                        .decision("CONTINUE")
                        .shouldEnd(false)
                        .isFinal(true)
                        .build()
        );

        SubmitAttemptResponse response = f.service.submitAnswer(1L, 7L, request());

        assertThat(response.getDecision()).isEqualTo("continue");
        ArgumentCaptor<DecisionResolution> resolutionCaptor = ArgumentCaptor.forClass(DecisionResolution.class);
        verify(f.persistenceService).persist(eq(1L), any(), any(), resolutionCaptor.capture());
        DecisionResolution resolution = resolutionCaptor.getValue();
        assertThat(resolution.getRepairAttempts()).isEqualTo(1);
        assertThat(resolution.getEffectivePlan().getEffectiveDecisionSource())
                .isEqualTo(DecisionExecutionPlan.EffectiveDecisionSource.REPAIRED_AI);
        assertThat(resolution.getRepairInput().getRetrievedMaterials()).isEmpty();
        assertThat(resolution.getRepairInput().getCoveredKnowledgeSummary()).isEmpty();
        verify(f.reportService, never()).generateAsync(any());
    }

    @Test
    @DisplayName("repair failure should fallback to behavioral continue plan")
    void repairFailure_shouldFallbackToBehavioralContinuePlan() {
        Fixture f = fixture(baseSession(Map.of()), principleQuestion());
        when(f.aiClient.callEvaluationDecision(any()))
                .thenReturn(aiResult("""
                        {"decisionReason":"raw","interviewAction":"CONTINUE","finalDecision":"BAD","nextFocus":"缓存一致性","targetDomainCode":"","newCoveredDomains":[],"newCoveredPoints":[],"retrievalPlans":[]}
                        """))
                .thenReturn(aiResult("""
                        {"decisionReason":"repair","interviewAction":"CONTINUE","finalDecision":"BAD2","nextFocus":"缓存一致性","targetDomainCode":"","newCoveredDomains":[],"newCoveredPoints":[],"retrievalPlans":[]}
                        """));
        when(f.persistenceService.persist(eq(1L), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptId("attempt-1")
                        .decision("CONTINUE")
                        .shouldEnd(false)
                        .isFinal(true)
                        .build()
        );

        SubmitAttemptResponse response = f.service.submitAnswer(1L, 7L, request());

        assertThat(response.getDecision()).isEqualTo("continue");
        ArgumentCaptor<DecisionResolution> resolutionCaptor = ArgumentCaptor.forClass(DecisionResolution.class);
        verify(f.persistenceService).persist(eq(1L), any(), any(), resolutionCaptor.capture());
        DecisionResolution resolution = resolutionCaptor.getValue();
        assertThat(resolution.getEffectivePlan().getEffectiveDecisionSource())
                .isEqualTo(DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK);
        assertThat(resolution.getEffectivePlan().getStrategyCode()).isEqualTo("S_ENTER_BEHAVIORAL");
        assertThat(resolution.getEffectiveOutput().getDecisionReason())
                .contains("系统降级为行为题继续建立候选人的真实事件画像");
        verify(f.reportService, never()).generateAsync(any());
    }

    @Test
    @DisplayName("second consecutive fallback should end interview with system error")
    void secondConsecutiveFallback_shouldEndInterviewWithSystemError() {
        Map<String, Object> fallbackState = new LinkedHashMap<>();
        fallbackState.put("rotationIndex", 1);
        fallbackState.put("consecutiveFallbackCount", 1);
        fallbackState.put("totalFallbackCount", 1);
        Fixture f = fixture(baseSession(Map.of("decision_fallback_state", fallbackState)), principleQuestion());
        InterviewSession endSession = new InterviewSession();
        endSession.setId(1L);
        endSession.setStatus("report_generating");
        when(f.sessionMapper.selectById(1L)).thenReturn(f.session, endSession);
        when(f.aiClient.callEvaluationDecision(any()))
                .thenReturn(aiResult("""
                        {"decisionReason":"raw","interviewAction":"CONTINUE","finalDecision":"BAD","nextFocus":"缓存一致性","targetDomainCode":"","newCoveredDomains":[],"newCoveredPoints":[],"retrievalPlans":[]}
                        """))
                .thenReturn(aiResult("""
                        {"decisionReason":"repair","interviewAction":"CONTINUE","finalDecision":"BAD2","nextFocus":"缓存一致性","targetDomainCode":"","newCoveredDomains":[],"newCoveredPoints":[],"retrievalPlans":[]}
                        """));
        when(f.persistenceService.persist(eq(1L), any(), any(), any())).thenReturn(
                AnswerSubmitPersistenceService.PersistedAttemptResult.builder()
                        .attemptId("attempt-1")
                        .decision("WRAPUP")
                        .shouldEnd(true)
                        .isFinal(true)
                        .build()
        );

        SubmitAttemptResponse response = f.service.submitAnswer(1L, 7L, request());

        assertThat(response.getDecision()).isEqualTo("wrapup");
        ArgumentCaptor<DecisionResolution> resolutionCaptor = ArgumentCaptor.forClass(DecisionResolution.class);
        verify(f.persistenceService).persist(eq(1L), any(), any(), resolutionCaptor.capture());
        DecisionResolution resolution = resolutionCaptor.getValue();
        assertThat(resolution.getEffectivePlan().getInterviewAction()).isEqualTo("WRAPUP");
        assertThat(resolution.getEffectivePlan().getTerminationSource())
                .isEqualTo(DecisionExecutionPlan.TerminationSource.SYSTEM_ERROR);
        verify(f.reportService).generateAsync(1L);
    }

    private Fixture fixture(InterviewSession session, InterviewQuestion question) {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);
        when(attemptMapper.selectByAttemptId("attempt-1")).thenReturn(null);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        when(questionMapper.selectById(101L)).thenReturn(question);
        when(questionMapper.selectList(any())).thenReturn(List.of(question));
        when(attemptMapper.selectBySessionId(1L)).thenReturn(List.of());

        DecisionExecutionPlanBuilder planBuilder = new DecisionExecutionPlanBuilder();
        AnswerSubmitService service = new AnswerSubmitService(
                aiClient,
                sessionMapper,
                questionMapper,
                attemptMapper,
                persistenceService,
                reportService,
                new InterviewDebugTraceService(new ObjectMapper()),
                new RemainingDomainMenuBuilder(),
                new AvailableStrategyAssembler(),
                planBuilder,
                new DecisionRepairOrchestrator(aiClient, planBuilder),
                new SystemFallbackPlanBuilder()
        );
        return new Fixture(service, aiClient, sessionMapper, persistenceService, reportService, session);
    }

    private InterviewSession baseSession(Map<String, Object> extraLedger) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("quota_state", QuotaStateSupport.initialQuotaState());
        ledger.put("covered_points", List.of());
        ledger.put("domain_states", List.of(
                Map.of("domainCode", "DOMAIN_REDIS", "status", "UNASKED")
        ));
        ledger.put("max_questions", 20);
        ledger.putAll(extraLedger);

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(7L);
        session.setStatus("in_progress");
        session.setTargetRole("JAVA_BACKEND");
        session.setExperienceLevel("FRESH_GRAD");
        session.setCurrentQuestionNo(1);
        session.setStateLedgerJson(ledger);
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainCode", "DOMAIN_REDIS",
                                "domainName", "Redis 缓存",
                                "focusPoints", List.of("缓存一致性")
                        )
                )
        ));
        return session;
    }

    private InterviewQuestion principleQuestion() {
        InterviewQuestion question = new InterviewQuestion();
        question.setId(101L);
        question.setSessionId(1L);
        question.setQuestionType("PRINCIPLE");
        question.setStem("解释缓存一致性");
        question.setExpectedPoints(List.of("双删"));
        question.setGenerationContextJson(Map.of(
                "domainCode", "DOMAIN_REDIS",
                "domainName", "Redis 缓存",
                "focusPoint", "缓存一致性"
        ));
        return question;
    }

    private SubmitAttemptRequest request() {
        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-1");
        request.setQuestionId(101L);
        request.setAnswerText("回答");
        request.setIsFinal(true);
        return request;
    }

    private AiCallResult<EvaluationDecisionOutput> aiResult(String rawJson) {
        return AiCallResult.<EvaluationDecisionOutput>builder()
                .output(null)
                .rawResponse(rawJson)
                .build();
    }

    private record Fixture(AnswerSubmitService service,
                           AiClient aiClient,
                           InterviewSessionMapper sessionMapper,
                           AnswerSubmitPersistenceService persistenceService,
                           ReportGenerationService reportService,
                           InterviewSession session) {
    }
}
