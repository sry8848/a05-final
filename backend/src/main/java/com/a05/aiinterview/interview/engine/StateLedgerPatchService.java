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
import java.util.LinkedHashSet;
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
                                         DecisionExecutionPlan executionPlan,
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

        Map<String, Object> normalizedLedger = normalizeLedgerForReduction(session);
        LedgerMutation mutation = buildMutation(session, currentQuestion, evalOutput, executionPlan, answerText);
        Map<String, Object> newLedger = stateLedgerReducer.reduce(
                normalizedLedger, mutation, attemptId, evidenceQuestionId);
        DecisionFallbackStateSupport.applyPlan(newLedger, executionPlan);
        Map<String, Object> diff = stateLedgerDiffService.diff(normalizedLedger, newLedger);

        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStateLedgerJson(newLedger);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);

        syncSkillState(sessionId, mutation, newLedger, evidenceQuestionId);
        logReductionDebug(sessionId, currentQuestion, mutation, diff, normalizedLedger, newLedger, attemptId);

        return ReductionAudit.builder()
                .newLedger(newLedger)
                .diff(diff)
                .domainClosureReason(resolveDomainClosureReason(mutation))
                .build();
    }

    private LedgerMutation buildMutation(InterviewSession session,
                                         InterviewQuestion currentQuestion,
                                         EvaluationDecisionOutput evalOutput,
                                         DecisionExecutionPlan executionPlan,
                                         String answerText) {
        Map<String, Object> generationContext = currentQuestion.getGenerationContextJson() != null
                ? currentQuestion.getGenerationContextJson()
                : Map.of();
        Map<String, Object> ledger = session.getStateLedgerJson() != null ? session.getStateLedgerJson() : Map.of();
        String currentDomainCode = resolveCurrentDomainCode(session, currentQuestion);
        String currentFocus = firstNonBlank(
                asString(generationContext.get("focusPoint")),
                currentQuestion.getTargetSkill(),
                asString(ledger.get("current_focus")));

        String currentItemKey = firstNonBlank(
                asString(generationContext.get("activeItemKey")),
                asString(ledger.get("active_item_key")));
        String currentItemType = firstNonBlank(
                asString(generationContext.get("activeItemType")),
                asString(ledger.get("active_item_type")));
        String currentItemName = firstNonBlank(
                asString(generationContext.get("activeItemName")),
                asString(ledger.get("active_item_name")));

        return LedgerMutation.builder()
                .questionType(currentQuestion.getQuestionType())
                .currentDomainCode(currentDomainCode)
                .currentFocus(currentFocus)
                .currentItemKey(currentItemKey)
                .currentItemType(currentItemType)
                .currentItemName(currentItemName)
                .nextFocus(firstNonBlank(executionPlan != null ? executionPlan.getNextFocus() : null, evalOutput.getNextFocus()))
                .nextItemKey(currentItemKey)
                .nextItemType(currentItemType)
                .nextItemName(currentItemName)
                .questionFamilyId(buildQuestionFamilyId(executionPlan))
                .skipCurrentQuestion("[skip]".equals(answerText))
                .interviewAction(firstNonBlank(executionPlan != null ? executionPlan.getInterviewAction() : null, evalOutput.getInterviewAction()))
                .newCoveredDomains(toCoveredDomains(executionPlan, evalOutput))
                .newCoveredPoints(executionPlan != null && executionPlan.getNewCoveredPoints() != null
                        ? executionPlan.getNewCoveredPoints()
                        : evalOutput.getNewCoveredPoints())
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
        for (String domainCode : collectSkillStateSyncDomainCodes(mutation)) {
            syncSingleSkillState(sessionId, domainCode, newLedger, evidenceQuestionId);
        }
    }

    private void syncSingleSkillState(Long sessionId,
                                      String domainCode,
                                      Map<String, Object> newLedger,
                                      Long evidenceQuestionId) {
        Long currentDomainId = resolveCurrentDomainId(newLedger, domainCode);
        if (currentDomainId == null) {
            return;
        }
        SessionSkillState existing = sessionSkillStateMapper.selectOne(
                new LambdaQueryWrapper<SessionSkillState>()
                        .eq(SessionSkillState::getSessionId, sessionId)
                        .eq(SessionSkillState::getDomainId, currentDomainId));
        if (existing == null) {
            return;
        }

        Map<String, Object> domainState = findDomainState(newLedger, domainCode);
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

    private List<String> collectSkillStateSyncDomainCodes(LedgerMutation mutation) {
        if (mutation == null) {
            return List.of();
        }
        LinkedHashSet<String> domainCodes = new LinkedHashSet<>();
        if (mutation.getCurrentDomainCode() != null && !mutation.getCurrentDomainCode().isBlank()) {
            domainCodes.add(mutation.getCurrentDomainCode().trim());
        }
        if (mutation.getNewCoveredDomains() != null) {
            for (LedgerMutation.CoveredDomainByCode coveredDomain : mutation.getNewCoveredDomains()) {
                if (coveredDomain == null || coveredDomain.getDomainCode() == null || coveredDomain.getDomainCode().isBlank()) {
                    continue;
                }
                domainCodes.add(coveredDomain.getDomainCode().trim());
            }
        }
        return new ArrayList<>(domainCodes);
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

    private String buildQuestionFamilyId(DecisionExecutionPlan executionPlan) {
        if (executionPlan == null || "WRAPUP".equalsIgnoreCase(executionPlan.getInterviewAction())) {
            return null;
        }
        String questionType = executionPlan.getTargetQuestionType() == null
                ? ""
                : executionPlan.getTargetQuestionType().trim().toUpperCase();
        String focus = executionPlan.getNextFocus() == null
                ? ""
                : executionPlan.getNextFocus().trim().replaceAll("\\s+", "_");
        if (questionType.isBlank() || focus.isBlank()) {
            return null;
        }
        return questionType + "." + focus;
    }

    private Map<String, Object> normalizeLedgerForReduction(InterviewSession session) {
        Map<String, Object> normalized = session.getStateLedgerJson() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(session.getStateLedgerJson());
        int askedTotal = session.getCurrentQuestionNo() != null
                ? Math.max(session.getCurrentQuestionNo(), 0)
                : QuotaStateSupport.toInt(normalized.get("asked_total"));
        normalized.put("asked_total", askedTotal);
        return normalized;
    }

    private String resolveCurrentDomainCode(InterviewSession session, InterviewQuestion question) {
        String domainCode = extractDomainCode(question != null ? question.getGenerationContextJson() : null);
        if (!domainCode.isBlank()) {
            return domainCode;
        }
        if (question != null && question.getDomainId() != null) {
            String fromSyllabus = resolveDomainCodeFromSyllabus(session, question.getDomainId());
            if (!fromSyllabus.isBlank()) {
                return fromSyllabus;
            }
        }
        return "";
    }

    private Long resolveCurrentDomainId(Map<String, Object> ledger, String domainCode) {
        Map<String, Object> domainState = findDomainState(ledger, domainCode);
        if (domainState == null) {
            return null;
        }
        return toLong(domainState.get("domainId"));
    }

    private String resolveDomainCodeFromSyllabus(InterviewSession session, Long domainId) {
        if (session == null || session.getSyllabusJson() == null || domainId == null) {
            return "";
        }
        Object rawDomains = session.getSyllabusJson().get("domains");
        if (!(rawDomains instanceof List<?> domains)) {
            return "";
        }
        for (Object domainObj : domains) {
            if (!(domainObj instanceof Map<?, ?> domain)) {
                continue;
            }
            if (Objects.equals(domainId, toLong(domain.get("domainId")))) {
                return asString(domain.get("domainCode"));
            }
        }
        return "";
    }

    private String extractDomainCode(Map<String, Object> generationContext) {
        if (generationContext == null) {
            return "";
        }
        Object domainCode = generationContext.get("domainCode");
        return domainCode instanceof String code ? code : "";
    }

    private List<LedgerMutation.CoveredDomainByCode> toCoveredDomains(DecisionExecutionPlan executionPlan,
                                                                      EvaluationDecisionOutput evalOutput) {
        List<EvaluationDecisionOutput.CoveredDomain> rawDomains = executionPlan != null && executionPlan.getNewCoveredDomains() != null
                ? executionPlan.getNewCoveredDomains()
                : evalOutput.getNewCoveredDomains();
        if (rawDomains == null || rawDomains.isEmpty()) {
            return List.of();
        }
        List<LedgerMutation.CoveredDomainByCode> coveredDomains = new ArrayList<>(rawDomains.size());
        for (EvaluationDecisionOutput.CoveredDomain rawDomain : rawDomains) {
            if (rawDomain == null || rawDomain.getDomainCode() == null || rawDomain.getDomainCode().isBlank()) {
                continue;
            }
            coveredDomains.add(LedgerMutation.CoveredDomainByCode.builder()
                    .domainCode(rawDomain.getDomainCode().trim())
                    .domainName(firstNonBlank(rawDomain.getDomainName(), ""))
                    .build());
        }
        return coveredDomains;
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
        summary.put("quota_state", ledger.get("quota_state"));
        summary.put("decision_fallback_state", ledger.get("decision_fallback_state"));
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

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
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
