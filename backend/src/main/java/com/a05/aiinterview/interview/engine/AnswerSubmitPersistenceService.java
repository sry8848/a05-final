package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.event.FinalAttemptPersistedEvent;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回答提交持久化事务服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerSubmitPersistenceService {

    public static final String DETAIL_STATUS_PENDING = "pending";

    private final InterviewAttemptMapper interviewAttemptMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final StateLedgerPatchService stateLedgerPatchService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public PersistedAttemptResult persist(Long sessionId,
                                          InterviewQuestion currentQuestion,
                                          SubmitAttemptRequest request,
                                          EvaluationDecisionOutput evalOutput) {
        StateLedgerPatchService.ReductionAudit reductionAudit = stateLedgerPatchService.applyReduction(
                sessionId,
                evalOutput,
                currentQuestion,
                request.getAttemptId(),
                currentQuestion.getId(),
                request.getAnswerText()
        );

        InterviewAttempt attempt = saveAttempt(sessionId, currentQuestion.getId(), request, evalOutput, reductionAudit);
        markQuestionStatus(currentQuestion.getId(), request.getAnswerText());

        boolean shouldEnd = "WRAPUP".equalsIgnoreCase(evalOutput.getInterviewAction());
        if (shouldEnd) {
            markSessionFinishing(sessionId);
        }

        boolean isFinal = Boolean.TRUE.equals(request.getIsFinal());
        if (isFinal) {
            eventPublisher.publishEvent(new FinalAttemptPersistedEvent(
                    sessionId,
                    currentQuestion.getId(),
                    attempt.getId(),
                    attempt.getAttemptId()
            ));
        }

        return PersistedAttemptResult.builder()
                .attemptDbId(attempt.getId())
                .attemptId(attempt.getAttemptId())
                .isFinal(isFinal)
                .shouldEnd(shouldEnd)
                .decision(evalOutput.getInterviewAction())
                .build();
    }

    private InterviewAttempt saveAttempt(Long sessionId,
                                         Long questionId,
                                         SubmitAttemptRequest request,
                                         EvaluationDecisionOutput evalOutput,
                                         StateLedgerPatchService.ReductionAudit reductionAudit) {
        Map<String, Object> evaluationJson = new LinkedHashMap<>();
        evaluationJson.put("interviewAction", evalOutput.getInterviewAction());
        evaluationJson.put("answerSummary", evalOutput.getAnswerSummary());
        evaluationJson.put("answerAssessment", evalOutput.getAnswerAssessment());
        evaluationJson.put("decisionReason", evalOutput.getDecisionReason());
        evaluationJson.put("candidateStrategies", evalOutput.getCandidateStrategies());
        evaluationJson.put("finalDecision", evalOutput.getFinalDecision());
        evaluationJson.put("nextEntryAction", evalOutput.getNextEntryAction());
        evaluationJson.put("nextQuestionType", evalOutput.getNextQuestionType());
        evaluationJson.put("nextFocus", evalOutput.getNextFocus());
        evaluationJson.put("expectedAnswerPoints", evalOutput.getExpectedAnswerPoints());
        evaluationJson.put("newCoveredDomains", evalOutput.getNewCoveredDomains());
        evaluationJson.put("newCoveredPoints", evalOutput.getNewCoveredPoints());
        evaluationJson.put("retrievalPlans", evalOutput.getRetrievalPlans());
        if (reductionAudit != null) {
            evaluationJson.put("ledgerDiff", reductionAudit.getDiff());
            evaluationJson.put("reducedLedger", reductionAudit.getNewLedger());
            evaluationJson.put("domainClosureReason", reductionAudit.getDomainClosureReason());
        }
        if (request.getRawAsrText() != null && !request.getRawAsrText().isBlank()) {
            evaluationJson.put("rawAsrText", request.getRawAsrText());
        }
        if (request.getAsrCorrectionChanges() != null && !request.getAsrCorrectionChanges().isEmpty()) {
            evaluationJson.put("asrCorrectionChanges", request.getAsrCorrectionChanges());
        }

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setAttemptId(request.getAttemptId());
        attempt.setAnswerText(request.getAnswerText());
        attempt.setIsFinal(Boolean.TRUE.equals(request.getIsFinal()));
        attempt.setEvaluationJson(evaluationJson);
        attempt.setDetailEvaluationStatus(DETAIL_STATUS_PENDING);
        attempt.setCreatedAt(LocalDateTime.now());
        interviewAttemptMapper.insert(attempt);
        return attempt;
    }

    private void markQuestionStatus(Long questionId, String answerText) {
        InterviewQuestion update = new InterviewQuestion();
        update.setId(questionId);
        update.setStatus("[skip]".equals(answerText) ? "skipped" : "answered");
        update.setUpdatedAt(LocalDateTime.now());
        interviewQuestionMapper.updateById(update);
    }

    private void markSessionFinishing(Long sessionId) {
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStatus("report_generating");
        update.setFinishedAt(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);
    }

    @Data
    @Builder
    public static class PersistedAttemptResult {
        private Long attemptDbId;
        private String attemptId;
        private boolean isFinal;
        private boolean shouldEnd;
        private String decision;
    }
}
