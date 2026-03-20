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

        if ("wrapup".equalsIgnoreCase(output.getDecision())) {
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
                .decision(output.getDecision())
                .streamAttemptId(request.getAttemptId())
                .sessionStatus("in_progress")
                .build();
    }

    private EvaluationDecisionOutput buildSkipDecision(InterviewSession session, InterviewQuestion question) {
        boolean shouldEnd = shouldForceEndByMaxQuestions(session);
        EvaluationDecisionOutput.NextQuestionStrategy nextStrategy = shouldEnd
                ? null
                : buildNextStrategy(session, question, resolveDomainCode(question));
        if (!shouldEnd && nextStrategy == null) {
            shouldEnd = true;
        }

        return EvaluationDecisionOutput.builder()
                .answerAssessment("candidate skipped current question")
                .answerVerdict("WEAK")
                .decision(shouldEnd ? "wrapup" : "broaden")
                .targetFocus(nextStrategy != null ? nextStrategy.getFocusPoint() : null)
                .targetAngle("implementation")
                .difficultyAdjustment("same")
                .nextQuestionGoal(shouldEnd ? "end interview after skip" : "broaden to next available domain after skip")
                .nextDomainId(nextStrategy != null ? nextStrategy.getNextDomainId() : null)
                .nextDomainCode(nextStrategy != null ? nextStrategy.getNextDomainCode() : null)
                .nextDomainName(nextStrategy != null ? nextStrategy.getNextDomainName() : null)
                .questionType(nextStrategy != null ? nextStrategy.getQuestionType() : null)
                .focusPoint(nextStrategy != null ? nextStrategy.getFocusPoint() : null)
                .domainOutcome(shouldEnd ? "covered" : "continue")
                .reasoning("question skipped")
                .build();
    }

    @SuppressWarnings("unchecked")
    private EvaluationDecisionOutput.NextQuestionStrategy buildNextStrategy(
            InterviewSession session,
            InterviewQuestion question,
            String fallbackDomainCode) {
        String currentDomainCode = fallbackDomainCode;
        String nextDomainCode = null;
        String nextDomainName = null;
        Long nextDomainId = null;
        String inProgressFallback = null;
        Map<String, String> statusByCode = extractDomainStatusMap(session);
        Object domainsObj = session.getSyllabusJson() != null ? session.getSyllabusJson().get("domains") : null;
        if (domainsObj instanceof List<?> domains) {
            for (Object domainObj : domains) {
                if (!(domainObj instanceof Map<?, ?> domain)) {
                    continue;
                }
                Object codeObj = domain.get("domainCode");
                if (!(codeObj instanceof String c) || c.equals(currentDomainCode)) {
                    continue;
                }
                String status = statusByCode.getOrDefault(c, "");
                if ("COVERED".equalsIgnoreCase(status)) {
                    continue;
                }
                if (status.isBlank() || "UNASKED".equalsIgnoreCase(status)) {
                    nextDomainCode = c;
                    break;
                }
                if ("IN_PROGRESS".equalsIgnoreCase(status) && inProgressFallback == null) {
                    inProgressFallback = c;
                }
            }
        }
        if (!StringUtils.hasText(nextDomainCode)) {
            nextDomainCode = inProgressFallback;
        }
        if (!StringUtils.hasText(nextDomainCode)) {
            return null;
        }
        nextDomainName = nextDomainCode;

        if (session.getSyllabusJson() != null) {
            domainsObj = session.getSyllabusJson().get("domains");
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

        String targetDepth = resolveStartingDepth(session.getExperienceLevel());
        String targetSkill = nextDomainName;

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

    @SuppressWarnings("unchecked")
    private Map<String, String> extractDomainStatusMap(InterviewSession session) {
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        if (session.getStateLedgerJson() == null) {
            return result;
        }
        Object domainStatesObj = session.getStateLedgerJson().get("domain_states");
        if (!(domainStatesObj instanceof List<?> states)) {
            return result;
        }
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> state)) {
                continue;
            }
            Object codeObj = state.get("domain_id");
            if (!(codeObj instanceof String code) || !StringUtils.hasText(code)) {
                continue;
            }
            result.put(code, String.valueOf(state.get("status")));
        }
        return result;
    }

    private String resolveStartingDepth(String experienceLevel) {
        if ("FRESH_GRAD".equalsIgnoreCase(experienceLevel)
                || "INTERN".equalsIgnoreCase(experienceLevel)) {
            return "L1";
        }
        return "L2";
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
        String decision = "broaden";
        if (existing.getEvaluationJson() != null) {
            Object decisionObj = existing.getEvaluationJson().get("decision");
            if (decisionObj instanceof String s && StringUtils.hasText(s)) {
                decision = s;
            }
        }
        boolean isEnd = "wrapup".equalsIgnoreCase(decision);
        return SubmitAttemptResponse.builder()
                .attemptId(existing.getAttemptId())
                .decision(decision)
                .streamAttemptId(isEnd ? null : existing.getAttemptId())
                .sessionStatus(isEnd ? "report_generating" : "in_progress")
                .build();
    }
}
