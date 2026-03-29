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
                                          DecisionResolution resolution) {
        EvaluationDecisionOutput evalOutput = resolution.getEffectiveOutput();
        StateLedgerPatchService.ReductionAudit reductionAudit = stateLedgerPatchService.applyReduction(
                sessionId,
                evalOutput,
                resolution.getEffectivePlan(),
                currentQuestion,
                request.getAttemptId(),
                currentQuestion.getId(),
                request.getAnswerText()
        );

        InterviewAttempt attempt = saveAttempt(sessionId, currentQuestion.getId(), request, resolution, reductionAudit);
        markQuestionStatus(currentQuestion.getId(), request.getAnswerText());

        boolean shouldEnd = "WRAPUP".equalsIgnoreCase(resolution.getEffectivePlan().getInterviewAction());
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
                .decision(resolution.getEffectivePlan().getInterviewAction())
                .build();
    }

    private InterviewAttempt saveAttempt(Long sessionId,
                                         Long questionId,
                                         SubmitAttemptRequest request,
                                         DecisionResolution resolution,
                                         StateLedgerPatchService.ReductionAudit reductionAudit) {
        EvaluationDecisionOutput evalOutput = resolution.getEffectiveOutput();
        Map<String, Object> evaluationJson = new LinkedHashMap<>();
        evaluationJson.put("interviewAction", evalOutput.getInterviewAction());
        evaluationJson.put("decisionReason", evalOutput.getDecisionReason());
        evaluationJson.put("finalDecision", evalOutput.getFinalDecision());
        evaluationJson.put("nextFocus", evalOutput.getNextFocus());
        evaluationJson.put("nextItemType", evalOutput.getNextItemType());
        evaluationJson.put("nextItemName", evalOutput.getNextItemName());
        evaluationJson.put("nextProjectPoint", evalOutput.getNextProjectPoint());
        evaluationJson.put("newCoveredDomains", evalOutput.getNewCoveredDomains());
        evaluationJson.put("newCoveredPoints", evalOutput.getNewCoveredPoints());
        evaluationJson.put("retrievalPlans", evalOutput.getRetrievalPlans());
        evaluationJson.put("rawAiOutput", resolution.getRawOutput());
        evaluationJson.put("decisionValidation", resolution.getValidationAudit());
        evaluationJson.put("decisionRepairAudit", buildRepairAudit(resolution));
        evaluationJson.put("effectiveDecisionPlan", resolution.getEffectivePlan());
        evaluationJson.put("effectiveDecisionSource", resolution.getEffectivePlan() != null
                ? String.valueOf(resolution.getEffectivePlan().getEffectiveDecisionSource())
                : "");
        evaluationJson.put("terminationSource", resolution.getEffectivePlan() != null && resolution.getEffectivePlan().getTerminationSource() != null
                ? String.valueOf(resolution.getEffectivePlan().getTerminationSource())
                : "");
        evaluationJson.put("terminationReason", resolution.getEffectivePlan() != null && resolution.getEffectivePlan().getTerminationReason() != null
                ? String.valueOf(resolution.getEffectivePlan().getTerminationReason())
                : "");
        evaluationJson.put("repairAttempts", resolution.getRepairAttempts());
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

    private Map<String, Object> buildRepairAudit(DecisionResolution resolution) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("repairAttempts", resolution.getRepairAttempts());
        audit.put("repairInput", resolution.getRepairInput());
        audit.put("repairOutput", resolution.getRepairedOutput());
        audit.put("repairErrors", resolution.getRepairErrors());
        return audit;
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
