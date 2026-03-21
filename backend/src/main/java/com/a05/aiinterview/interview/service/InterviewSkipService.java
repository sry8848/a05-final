package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.dto.SkipAndNextRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptRequest;
import com.a05.aiinterview.interview.dto.SubmitAttemptResponse;
import com.a05.aiinterview.interview.engine.AnswerSubmitPersistenceService;
import com.a05.aiinterview.interview.engine.ReportGenerationService;
import com.a05.aiinterview.interview.entity.InterviewAttempt;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.mapper.InterviewAttemptMapper;
import com.a05.aiinterview.interview.mapper.InterviewQuestionMapper;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSkipService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final InterviewAttemptMapper interviewAttemptMapper;
    private final AnswerSubmitPersistenceService answerSubmitPersistenceService;
    private final ReportGenerationService reportGenerationService;

    public SubmitAttemptResponse skipAndNext(Long sessionId,
                                             Long questionId,
                                             Long userId,
                                             SkipAndNextRequest request) {
        InterviewAttempt existing = interviewAttemptMapper.selectByAttemptId(request.getAttemptId());
        if (existing != null) {
            return buildIdempotentResponse(existing);
        }

        InterviewSession session = interviewSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("面试会话不存在或无权访问");
        }
        if (!"in_progress".equals(session.getStatus())) {
            throw new IllegalArgumentException("面试会话状态异常，当前状态: " + session.getStatus());
        }

        InterviewQuestion question = interviewQuestionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new IllegalArgumentException("题目不存在或不属于该会话");
        }

        EvaluationDecisionOutput output = buildSkipDecision(session, question);

        SubmitAttemptRequest submit = new SubmitAttemptRequest();
        submit.setQuestionId(questionId);
        submit.setAttemptId(request.getAttemptId());
        submit.setAnswerText("[skip]");
        submit.setIsFinal(true);

        answerSubmitPersistenceService.persist(sessionId, question, submit, output);

        if ("WRAPUP".equalsIgnoreCase(output.getInterviewAction())) {
            reportGenerationService.generateAsync(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .decision("wrapup")
                    .streamAttemptId(null)
                    .sessionStatus("report_generating")
                    .build();
        }

        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .decision("continue")
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .build();
    }

    private EvaluationDecisionOutput buildSkipDecision(InterviewSession session, InterviewQuestion question) {
        boolean shouldEnd = shouldForceEndByMaxQuestions(session);
        String domainName = resolveDomainName(session, question);
        return EvaluationDecisionOutput.builder()
                .interviewAction(shouldEnd ? "WRAPUP" : "CONTINUE")
                .answerSummary("")
                .answerAssessment("候选人跳过了当前问题")
                .decisionReason(shouldEnd ? "当前已达到题量上限，跳过后直接结束面试。" : "当前题被跳过，切换到下一个自然方向。")
                .candidateStrategies(List.of(shouldEnd ? "结束面试" : "切到下一个方向"))
                .finalDecision(shouldEnd ? "结束面试" : "切到下一个方向")
                .nextQuestionType(shouldEnd ? "" : "THEORY")
                .nextFocus(shouldEnd ? "" : domainName)
                .expectedAnswerPoints(List.of())
                .possibleNextMoves(List.of())
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .newCandidatePointsByDomain(List.of())
                .retrievalPlans(List.of())
                .build();
    }

    private boolean shouldForceEndByMaxQuestions(InterviewSession session) {
        int maxQuestions = toInt(session.getStateLedgerJson() != null
                ? session.getStateLedgerJson().get("max_questions")
                : null);
        if (maxQuestions <= 0) {
            return false;
        }
        int currentNo = session.getCurrentQuestionNo() != null ? session.getCurrentQuestionNo() : 0;
        return currentNo >= maxQuestions;
    }

    private SubmitAttemptResponse buildIdempotentResponse(InterviewAttempt existing) {
        Map<String, Object> evaluationJson = existing.getEvaluationJson() != null ? existing.getEvaluationJson() : Map.of();
        String interviewAction = evaluationJson.get("interviewAction") instanceof String value ? value : "";
        boolean wrapup = "WRAPUP".equalsIgnoreCase(interviewAction);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .decision(wrapup ? "wrapup" : "continue")
                .streamAttemptId(wrapup ? null : existing.getAttemptId())
                .sessionStatus(wrapup ? "report_generating" : "in_progress")
                .build();
    }

    @SuppressWarnings("unchecked")
    private String resolveDomainName(InterviewSession session, InterviewQuestion question) {
        if (session.getSyllabusJson() != null && question.getGenerationContextJson() != null) {
            Object rawDomains = session.getSyllabusJson().get("domains");
            Object rawCode = question.getGenerationContextJson().get("domainCode");
            if (rawDomains instanceof List<?> domains && rawCode instanceof String domainCode) {
                for (Object domainObj : domains) {
                    if (!(domainObj instanceof Map<?, ?> domain)) {
                        continue;
                    }
                    if (domainCode.equals(String.valueOf(domain.get("domainCode")))) {
                        return String.valueOf(domain.get("domainName"));
                    }
                }
            }
        }
        return question.getTargetSkill();
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
