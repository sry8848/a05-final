package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.SessionSkillState;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 账本写入服务。
 * 新版本只接受结构化 mutation，再交由 reducer 计算新账本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateLedgerPatchService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final SessionSkillStateMapper sessionSkillStateMapper;
    private final StateLedgerReducer stateLedgerReducer;
    private final StateLedgerDiffService stateLedgerDiffService;
    private final InterviewDebugTraceService interviewDebugTraceService;

    @Transactional(rollbackFor = Exception.class)
    public ReductionAudit applyReduction(Long sessionId,
                                         EvaluationDecisionOutput evalOutput,
                                         InterviewQuestion currentQuestion,
                                         String attemptId,
                                         Long evidenceQuestionId,
                                         String answerText) {
        InterviewSession session = interviewSessionMapper.selectForUpdate(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (session.getStateLedgerJson() == null) {
            throw new IllegalStateException("状态账本为空, sessionId=" + sessionId);
        }

        LedgerMutation mutation = buildMutation(session, currentQuestion, evalOutput, answerText);
        Map<String, Object> newLedger = stateLedgerReducer.reduce(
                session.getStateLedgerJson(), mutation, attemptId, evidenceQuestionId);
        Map<String, Object> diff = stateLedgerDiffService.diff(session.getStateLedgerJson(), newLedger);

        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStateLedgerJson(newLedger);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);

        syncSkillState(sessionId, mutation, newLedger, evidenceQuestionId);
        logReductionDebug(sessionId, currentQuestion, mutation, diff, session.getStateLedgerJson(), newLedger, attemptId);

        return ReductionAudit.builder()
                .newLedger(newLedger)
                .diff(diff)
                .domainClosureReason(resolveDomainClosureReason(mutation))
                .build();
    }

    private LedgerMutation buildMutation(InterviewSession session,
                                         InterviewQuestion currentQuestion,
                                         EvaluationDecisionOutput evalOutput,
                                         String answerText) {
        Map<String, Object> generationContext = currentQuestion.getGenerationContextJson() != null
                ? currentQuestion.getGenerationContextJson()
                : Map.of();
        Map<String, Object> ledger = session.getStateLedgerJson() != null ? session.getStateLedgerJson() : Map.of();

        String currentItemKey = firstNonBlank(
                asString(generationContext.get("activeItemKey")),
                asString(ledger.get("active_item_key")));
        String currentItemType = firstNonBlank(
                asString(generationContext.get("activeItemType")),
                asString(ledger.get("active_item_type")));
        String currentItemName = firstNonBlank(
                asString(generationContext.get("activeItemName")),
                asString(ledger.get("active_item_name")));
        String currentFocus = firstNonBlank(
                asString(generationContext.get("focusPoint")),
                currentQuestion.getTargetSkill(),
                asString(ledger.get("current_focus")));

        return LedgerMutation.builder()
                .questionType(currentQuestion.getQuestionType())
                .currentDomainCode(resolveDomainCode(currentQuestion))
                .currentDomainId(currentQuestion.getDomainId())
                .currentFocus(currentFocus)
                .currentItemKey(currentItemKey)
                .currentItemType(currentItemType)
                .currentItemName(currentItemName)
                .nextFocus(evalOutput.getNextFocus())
                .nextItemKey(currentItemKey)
                .nextItemType(currentItemType)
                .nextItemName(currentItemName)
                .questionFamilyId(buildQuestionFamilyId(evalOutput))
                .skipCurrentQuestion("[skip]".equals(answerText))
                .interviewAction(evalOutput.getInterviewAction())
                .newCoveredDomains(evalOutput.getNewCoveredDomains())
                .newCoveredPoints(evalOutput.getNewCoveredPoints())
                .newCandidatePointsByDomain(evalOutput.getNewCandidatePointsByDomain())
                .build();
    }

    private String resolveDomainClosureReason(LedgerMutation mutation) {
        if (mutation.getNewCoveredDomains() != null && !mutation.getNewCoveredDomains().isEmpty()) {
            return "COVERED";
        }
        if (mutation.isSkipCurrentQuestion()) {
            return "SKIPPED";
        }
        if ("WRAPUP".equalsIgnoreCase(mutation.getInterviewAction())) {
            return "WRAPUP";
        }
        return null;
    }

    private void syncSkillState(Long sessionId,
                                LedgerMutation mutation,
                                Map<String, Object> newLedger,
                                Long evidenceQuestionId) {
        if (mutation.getCurrentDomainId() == null) {
            return;
        }
        SessionSkillState existing = sessionSkillStateMapper.selectOne(
                new LambdaQueryWrapper<SessionSkillState>()
                        .eq(SessionSkillState::getSessionId, sessionId)
                        .eq(SessionSkillState::getDomainId, mutation.getCurrentDomainId()));
        if (existing == null) {
            return;
        }

        Map<String, Object> domainState = findDomainState(newLedger, mutation.getCurrentDomainCode());
        if (domainState == null) {
            return;
        }
        existing.setStatus(DomainStatus.fromLedgerValue(asString(domainState.get("status"))).getSkillStateValue());
        existing.setSaturated(Boolean.TRUE.equals(domainState.get("saturated")));
        existing.setTestedCount(existing.getTestedCount() == null ? 1 : existing.getTestedCount() + 1);
        if (evidenceQuestionId != null) {
            List<Long> refs = existing.getEvidenceRefs() != null ? new ArrayList<>(existing.getEvidenceRefs()) : new ArrayList<>();
            refs.add(evidenceQuestionId);
            existing.setEvidenceRefs(refs);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        sessionSkillStateMapper.updateById(existing);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findDomainState(Map<String, Object> ledger, String domainCode) {
        Object rawStates = ledger.get("domain_states");
        if (!(rawStates instanceof List<?> states)) {
            return null;
        }
        for (Object stateObj : states) {
            if (!(stateObj instanceof Map<?, ?> state)) {
                continue;
            }
            if (Objects.equals(domainCode, asString(state.get("domainCode")))) {
                return new LinkedHashMap<>((Map<String, Object>) state);
            }
        }
        return null;
    }

    private String buildQuestionFamilyId(EvaluationDecisionOutput evalOutput) {
        if (evalOutput == null || "WRAPUP".equalsIgnoreCase(evalOutput.getInterviewAction())) {
            return null;
        }
        String questionType = evalOutput.getNextQuestionType() == null
                ? ""
                : evalOutput.getNextQuestionType().trim().toUpperCase();
        String focus = evalOutput.getNextFocus() == null
                ? ""
                : evalOutput.getNextFocus().trim().replaceAll("\\s+", "_");
        if (questionType.isBlank() || focus.isBlank()) {
            return null;
        }
        return questionType + "." + focus;
    }

    private String resolveDomainCode(InterviewQuestion question) {
        if (question.getGenerationContextJson() == null) {
            return "";
        }
        Object domainCode = question.getGenerationContextJson().get("domainCode");
        return domainCode instanceof String code ? code : "";
    }

    private void logReductionDebug(Long sessionId,
                                   InterviewQuestion currentQuestion,
                                   LedgerMutation mutation,
                                   Map<String, Object> diff,
                                   Map<String, Object> oldLedger,
                                   Map<String, Object> newLedger,
                                   String attemptId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("attemptId", attemptId);
        payload.put("questionId", currentQuestion != null ? currentQuestion.getId() : null);
        payload.put("interviewAction", mutation.getInterviewAction());
        payload.put("currentDomainCode", mutation.getCurrentDomainCode());
        payload.put("currentFocus", mutation.getCurrentFocus());
        payload.put("nextFocus", mutation.getNextFocus());
        payload.put("ledgerBefore", summarizeLedger(oldLedger));
        payload.put("ledgerAfter", summarizeLedger(newLedger));
        payload.put("diff", diff);
        interviewDebugTraceService.recordQuestionStage(
                sessionId,
                currentQuestion != null ? currentQuestion.getId() : null,
                attemptId,
                "ledgerPatch",
                payload,
                buildLedgerDebugSummary(mutation, diff)
        );
    }

    private Map<String, Object> buildLedgerDebugSummary(LedgerMutation mutation, Map<String, Object> diff) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("currentFocus", asString(firstNonBlank(mutation.getCurrentFocus(), "")));
        summary.put("nextFocus", asString(firstNonBlank(mutation.getNextFocus(), "")));
        summary.put("diffKeys", diff == null ? List.of() : new ArrayList<>(diff.keySet()));
        return summary;
    }

    private Map<String, Object> summarizeLedger(Map<String, Object> ledger) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (ledger == null) {
            return summary;
        }
        summary.put("asked_total", ledger.get("asked_total"));
        summary.put("current_focus", ledger.get("current_focus"));
        summary.put("active_item_key", ledger.get("active_item_key"));
        summary.put("covered_domains", ledger.get("covered_domains"));
        summary.put("covered_points", ledger.get("covered_points"));
        return summary;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReductionAudit {
        private Map<String, Object> newLedger;
        private Map<String, Object> diff;
        private String domainClosureReason;
    }
}
