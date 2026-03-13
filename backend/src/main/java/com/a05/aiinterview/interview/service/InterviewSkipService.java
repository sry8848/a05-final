package com.a05.aiinterview.interview.service;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
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
import org.springframework.util.StringUtils;

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

        if ("END".equals(output.getSignal())) {
            reportGenerationService.generateAsync(sessionId);
            return SubmitAttemptResponse.builder()
                    .attemptId(request.getAttemptId())
                    .evaluationSignal("END")
                    .streamAttemptId(null)
                    .sessionStatus("report_generating")
                    .build();
        }

        return SubmitAttemptResponse.builder()
                .attemptId(request.getAttemptId())
                .evaluationSignal(output.getSignal())
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .build();
    }

    private EvaluationDecisionOutput buildSkipDecision(InterviewSession session, InterviewQuestion question) {
        String domainCode = resolveDomainCode(question);
        boolean shouldEnd = shouldForceEndByMaxQuestions(session);

        EvaluationDecisionOutput.LedgerPatch patch = EvaluationDecisionOutput.LedgerPatch.builder()
                .domainCode(domainCode)
                .domainId(question.getDomainId())
                .currentDepth(StringUtils.hasText(question.getTargetDepth()) ? question.getTargetDepth() : "L2")
                .domainStatus(shouldEnd ? DomainStatus.COVERED : DomainStatus.IN_PROGRESS)
                .saturated(shouldEnd)
                .questionType(question.getQuestionType())
                .build();

        EvaluationDecisionOutput.NextQuestionStrategy nextStrategy = shouldEnd
                ? null
                : buildNextStrategy(session, question, domainCode);

        return EvaluationDecisionOutput.builder()
                .domainCode(domainCode)
                .depthReached(StringUtils.hasText(question.getTargetDepth()) ? question.getTargetDepth() : "L2")
                .saturated(shouldEnd)
                .signal(shouldEnd ? "END" : "NEXT_DOMAIN")
                .patch(patch)
                .nextStrategy(nextStrategy)
                .reasoning("question skipped")
                .build();
    }

    @SuppressWarnings("unchecked")
    private EvaluationDecisionOutput.NextQuestionStrategy buildNextStrategy(
            InterviewSession session,
            InterviewQuestion question,
            String fallbackDomainCode) {
        String nextDomainCode = fallbackDomainCode;
        String nextDomainName = fallbackDomainCode;
        Long nextDomainId = question.getDomainId();

        if (session.getStateLedgerJson() != null) {
            Object domainStatesObj = session.getStateLedgerJson().get("domain_states");
            if (domainStatesObj instanceof List<?> states) {
                for (Object stateObj : states) {
                    if (!(stateObj instanceof Map<?, ?> state)) {
                        continue;
                    }
                    Object status = state.get("status");
                    Object code = state.get("domain_id");
                    if (code instanceof String c && (status == null || "UNASKED".equalsIgnoreCase(String.valueOf(status)))) {
                        nextDomainCode = c;
                        break;
                    }
                }
            }
        }

        if (session.getSyllabusJson() != null) {
            Object domainsObj = session.getSyllabusJson().get("domains");
            if (domainsObj instanceof List<?> domains) {
                for (Object domainObj : domains) {
                    if (!(domainObj instanceof Map<?, ?> domain)) {
                        continue;
                    }
                    if (!nextDomainCode.equals(domain.get("domainCode"))) {
                        continue;
                    }
                    Object idObj = domain.get("domainId");
                    if (idObj instanceof Number n) {
                        nextDomainId = n.longValue();
                    }
                    Object nameObj = domain.get("domainName");
                    if (nameObj instanceof String n && StringUtils.hasText(n)) {
                        nextDomainName = n;
                    }
                    break;
                }
            }
        }

        String targetDepth = StringUtils.hasText(question.getTargetDepth()) ? question.getTargetDepth() : "L2";
        String targetSkill = StringUtils.hasText(question.getTargetSkill()) ? question.getTargetSkill() : nextDomainName;

        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(nextDomainId)
                .nextDomainCode(nextDomainCode)
                .nextDomainName(nextDomainName)
                .questionType("PRINCIPLE")
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .targetSkill(targetSkill)
                .expectedPoints(question.getExpectedPoints())
                .focusPoint(targetSkill)
                .build();
    }

    private boolean shouldForceEndByMaxQuestions(InterviewSession session) {
        int maxQuestions = extractMaxQuestions(session.getStateLedgerJson());
        if (maxQuestions <= 0) {
            return false;
        }
        int currentNo = session.getCurrentQuestionNo() != null ? session.getCurrentQuestionNo() : 0;
        return currentNo >= maxQuestions;
    }

    private int extractMaxQuestions(Map<String, Object> ledger) {
        if (ledger == null) {
            return 0;
        }
        Object maxObj = ledger.get("max_questions");
        if (maxObj instanceof Number n) {
            return n.intValue();
        }
        if (maxObj != null) {
            try {
                return Integer.parseInt(String.valueOf(maxObj));
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String resolveDomainCode(InterviewQuestion question) {
        if (question.getGenerationContextJson() != null) {
            Object code = question.getGenerationContextJson().get("domainCode");
            if (code instanceof String c && StringUtils.hasText(c)) {
                return c;
            }
        }
        return "intro";
    }

    private SubmitAttemptResponse buildIdempotentResponse(InterviewAttempt existing) {
        String signal = "NEXT_DOMAIN";
        if (existing.getEvaluationJson() != null && existing.getEvaluationJson().get("signal") instanceof String s) {
            signal = s;
        }
        boolean isEnd = "END".equals(signal);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .evaluationSignal(signal)
                .streamAttemptId(isEnd ? null : existing.getAttemptId())
                .sessionStatus(isEnd ? "report_generating" : "in_progress")
                .build();
    }
}
