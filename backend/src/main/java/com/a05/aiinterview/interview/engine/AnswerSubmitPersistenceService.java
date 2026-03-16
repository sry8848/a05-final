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
    public PersistedAttemptResult persist(
            Long sessionId,
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

        boolean shouldEnd = "END".equals(evalOutput.getSignal());
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
                .evaluationSignal(evalOutput.getSignal())
                .shouldEnd(shouldEnd)
                .build();
    }

    private InterviewAttempt saveAttempt(Long sessionId,
                                         Long questionId,
                                         SubmitAttemptRequest request,
                                         EvaluationDecisionOutput evalOutput,
                                         StateLedgerPatchService.ReductionAudit reductionAudit) {
        Map<String, Object> evalSnapshot = new LinkedHashMap<>();
        evalSnapshot.put("signal", evalOutput.getSignal());
        evalSnapshot.put("passCurrentLevel", evalOutput.isPassCurrentLevel());
        evalSnapshot.put("deepen", evalOutput.isDeepen());
        if (evalOutput.getReasoning() != null && !evalOutput.getReasoning().isBlank()) {
            evalSnapshot.put("reasoning", evalOutput.getReasoning());
        }
        if (evalOutput.getSummary() != null && !evalOutput.getSummary().isBlank()) {
            evalSnapshot.put("summary", evalOutput.getSummary());
        }
        if (reductionAudit != null) {
            evalSnapshot.put("ledgerDiff", reductionAudit.getDiff());
            evalSnapshot.put("reducedLedger", reductionAudit.getNewLedger());
            if (reductionAudit.getDomainClosureReason() != null
                    && !reductionAudit.getDomainClosureReason().isBlank()) {
                evalSnapshot.put("domainClosureReason", reductionAudit.getDomainClosureReason());
            }
        }
        if (evalOutput.getNextStrategy() != null) {
            EvaluationDecisionOutput.NextQuestionStrategy strat = evalOutput.getNextStrategy();
            Map<String, Object> nextStrategySnapshot = new LinkedHashMap<>();
            nextStrategySnapshot.put("nextDomainId", strat.getNextDomainId());
            nextStrategySnapshot.put("nextDomainCode", strat.getNextDomainCode());
            nextStrategySnapshot.put("nextDomainName", strat.getNextDomainName());
            nextStrategySnapshot.put("questionType", strat.getQuestionType());
            nextStrategySnapshot.put("targetDepth", strat.getTargetDepth());
            nextStrategySnapshot.put("difficulty", strat.getDifficulty());
            nextStrategySnapshot.put("targetSkill", strat.getTargetSkill());
            nextStrategySnapshot.put("expectedPoints", strat.getExpectedPoints());
            nextStrategySnapshot.put("focusPoint", strat.getFocusPoint());
            evalSnapshot.put("nextStrategy", nextStrategySnapshot);
        }

        InterviewAttempt attempt = new InterviewAttempt();
        attempt.setSessionId(sessionId);
        attempt.setQuestionId(questionId);
        attempt.setAttemptId(request.getAttemptId());
        attempt.setAnswerText(request.getAnswerText());
        attempt.setIsFinal(Boolean.TRUE.equals(request.getIsFinal()));
        attempt.setEvaluationJson(evalSnapshot);
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
        private String evaluationSignal;
    }
}
