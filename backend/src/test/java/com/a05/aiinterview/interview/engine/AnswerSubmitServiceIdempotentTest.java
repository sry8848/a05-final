package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnswerSubmitServiceIdempotentTest {

    @Test
    void submitAnswer_shouldIgnoreLegacySignalWhenDecisionMissing() {
        AiClient aiClient = mock(AiClient.class);
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        InterviewQuestionMapper questionMapper = mock(InterviewQuestionMapper.class);
        InterviewAttemptMapper attemptMapper = mock(InterviewAttemptMapper.class);
        AnswerSubmitPersistenceService persistenceService = mock(AnswerSubmitPersistenceService.class);
        ReportGenerationService reportService = mock(ReportGenerationService.class);
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

        InterviewAttempt existing = new InterviewAttempt();
        existing.setAttemptId("attempt-legacy-signal");
        existing.setEvaluationJson(Map.of("signal", "END"));
        when(attemptMapper.selectByAttemptId("attempt-legacy-signal")).thenReturn(existing);

        SubmitAttemptRequest request = new SubmitAttemptRequest();
        request.setAttemptId("attempt-legacy-signal");
        request.setQuestionId(100L);
        request.setAnswerText("ignored");
        request.setIsFinal(true);

        SubmitAttemptResponse response = service.submitAnswer(1L, 2L, request);

        assertThat(response.getDecision()).isEqualTo("continue");
        assertThat(response.getStreamAttemptId()).isEqualTo("attempt-legacy-signal");
        assertThat(response.getSessionStatus()).isEqualTo("in_progress");
        verifyNoInteractions(aiClient, sessionMapper, questionMapper, persistenceService, reportService);
    }
}
