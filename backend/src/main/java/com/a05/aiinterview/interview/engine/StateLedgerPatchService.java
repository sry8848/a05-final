package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
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
import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>AI 不再回写正式账本；这里统一根据当前题和 AI 最小决策，使用 reducer 计算新账本后落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateLedgerPatchService {

    @Value("${interview.debug.enabled:false}")
    private boolean interviewDebugEnabled = false;

    @Value("${interview.debug.max-text-chars:1200}")
    private int interviewDebugMaxTextChars = 1200;

    private final InterviewSessionMapper interviewSessionMapper;
    private final SessionSkillStateMapper sessionSkillStateMapper;
    private final StateLedgerReducer stateLedgerReducer;
    private final StateLedgerDiffService stateLedgerDiffService;

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
        Map<String, Object> oldLedger = session.getStateLedgerJson();
        if (oldLedger == null) {
            throw new IllegalStateException("状态账本为空, sessionId=" + sessionId);
        }

        LedgerMutation mutation = buildMutation(session, currentQuestion, evalOutput, answerText);
        validateMutation(oldLedger, mutation);

        Map<String, Object> newLedger = stateLedgerReducer.reduce(oldLedger, mutation, attemptId, evidenceQuestionId);
        Map<String, Object> diff = stateLedgerDiffService.diff(oldLedger, newLedger);

        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStateLedgerJson(newLedger);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);

        if (!isIntro(currentQuestion.getQuestionType()) && mutation.getCurrentDomainId() != null) {
            syncSkillState(sessionId, mutation, newLedger, evidenceQuestionId);
        }

        logReductionDebug(sessionId, currentQuestion, mutation, diff, oldLedger, newLedger, attemptId);

        return ReductionAudit.builder()
                .newLedger(newLedger)
                .diff(diff)
                .domainClosureReason(resolveDomainClosureReason(currentQuestion, mutation))
                .build();
    }

    private LedgerMutation buildMutation(InterviewSession session,
                                         InterviewQuestion currentQuestion,
                                         EvaluationDecisionOutput evalOutput,
                                         String answerText) {
        return LedgerMutation.builder()
                .questionType(currentQuestion.getQuestionType())
                .currentDomainCode(resolveDomainCode(currentQuestion))
                .currentDomainId(currentQuestion.getDomainId())
                .currentTargetDepth(currentQuestion.getTargetDepth())
                .decision(evalOutput.getDecision())
                .answerVerdict(evalOutput.getAnswerVerdict())
                .domainOutcome(evalOutput.getDomainOutcome())
                .activeProjectId(resolveActiveProjectId(session, currentQuestion, evalOutput))
                .currentFocus(resolveCurrentFocus(evalOutput))
                .focusPoint(evalOutput.getFocusPoint())
                .questionFamilyId(resolveQuestionFamilyId(evalOutput))
                .skipCurrentQuestion("[skip]".equals(answerText))
                .statePatch(evalOutput.getStatePatch())
                .build();
    }

    private void validateMutation(Map<String, Object> oldLedger, LedgerMutation mutation) {
        String questionType = normalizeQuestionType(mutation.getQuestionType());
        mutation.setQuestionType(questionType);
        if (isIntro(questionType)) {
            return;
        }
        String domainCode = mutation.getCurrentDomainCode();
        if (domainCode == null || domainCode.isBlank()) {
            throw new IllegalArgumentException("非 INTRO 题必须提供 currentDomainCode");
        }
        if (!domainCodeExists(oldLedger, domainCode)) {
            throw new IllegalArgumentException("currentDomainCode 不存在于账本: " + domainCode);
        }
        if (mutation.getDecision() == null || mutation.getDecision().isBlank()) {
            throw new IllegalArgumentException("mutation.decision 不能为空");
        }
        if (mutation.getDomainOutcome() == null || mutation.getDomainOutcome().isBlank()) {
            throw new IllegalArgumentException("mutation.domainOutcome 不能为空");
        }
    }

    private String resolveDomainClosureReason(InterviewQuestion currentQuestion, LedgerMutation mutation) {
        if (isIntro(currentQuestion.getQuestionType())) {
            return null;
        }
        if (mutation.isSkipCurrentQuestion()) {
            return "NO_FURTHER_VALUE";
        }
        if ("wrapup".equalsIgnoreCase(mutation.getDecision())) {
            return "NO_FURTHER_VALUE";
        }
        if ("circuit_broken".equalsIgnoreCase(mutation.getDomainOutcome())) {
            return "FAILED_HARD";
        }
        if ("covered".equalsIgnoreCase(mutation.getDomainOutcome())) {
            return "DEPTH_REACHED";
        }
        return null;
    }

    private void syncSkillState(Long sessionId,
                                LedgerMutation mutation,
                                Map<String, Object> newLedger,
                                Long evidenceQuestionId) {
        SessionSkillState existing = sessionSkillStateMapper.selectOne(
                new LambdaQueryWrapper<SessionSkillState>()
                        .eq(SessionSkillState::getSessionId, sessionId)
                        .eq(SessionSkillState::getDomainId, mutation.getCurrentDomainId())
        );
        if (existing == null) {
            log.warn("session_skill_states 未找到对应行, sessionId={}, domainId={}", sessionId, mutation.getCurrentDomainId());
            return;
        }

        Map<String, Object> domainState = findDomainState(newLedger, mutation.getCurrentDomainCode());
        if (domainState == null) {
            return;
        }
        DomainStatus domainStatus = DomainStatus.fromLedgerValue(asString(domainState.get("status")));
        existing.setCurrentDepth(asString(domainState.get("current_depth")));
        existing.setSaturated(Boolean.TRUE.equals(domainState.get("saturated")));
        existing.setStatus(domainStatus.getSkillStateValue());
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
        Object statesObj = ledger.get("domain_states");
        if (!(statesObj instanceof List<?> states)) {
            return null;
        }
        for (Object item : states) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> state = new LinkedHashMap<>((Map<String, Object>) raw);
            if (Objects.equals(domainCode, asString(state.get("domain_id")))) {
                return state;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean domainCodeExists(Map<String, Object> ledger, String domainCode) {
        Object statesObj = ledger.get("domain_states");
        if (!(statesObj instanceof List<?> states)) {
            return false;
        }
        for (Object item : states) {
            if (item instanceof Map<?, ?> state && Objects.equals(domainCode, asString(state.get("domain_id")))) {
                return true;
            }
        }
        return false;
    }

    private String resolveDomainCode(InterviewQuestion question) {
        if (question.getGenerationContextJson() != null) {
            Object code = question.getGenerationContextJson().get("domainCode");
            if (code instanceof String domainCode && !domainCode.isBlank()) {
                return domainCode;
            }
        }
        return isIntro(question.getQuestionType()) ? null : "intro";
    }

    private String resolveActiveProjectId(InterviewSession session,
                                          InterviewQuestion currentQuestion,
                                          EvaluationDecisionOutput evalOutput) {
        if (evalOutput.getStatePatch() != null) {
            Object projectId = evalOutput.getStatePatch().get("activeProjectId");
            if (projectId instanceof String str && !str.isBlank()) {
                return str;
            }
        }
        Object existing = session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_project_id") : null;
        if (existing instanceof String projectId && !projectId.isBlank()) {
            return projectId;
        }
        if (!isIntro(currentQuestion.getQuestionType())) {
            return null;
        }
        if (session.getSyllabusJson() == null) {
            return null;
        }
        Object projectsObj = session.getSyllabusJson().get("projects");
        if (!(projectsObj instanceof List<?> projects) || projects.isEmpty()) {
            return null;
        }
        Object first = projects.getFirst();
        if (!(first instanceof Map<?, ?> project)) {
            return null;
        }
        Object projectId = project.get("projectId");
        if (projectId instanceof String str && !str.isBlank()) {
            return str;
        }
        return null;
    }

    private String resolveCurrentFocus(EvaluationDecisionOutput evalOutput) {
        if (evalOutput.getStatePatch() == null) {
            return null;
        }
        Object focus = evalOutput.getStatePatch().get("currentFocus");
        if (focus instanceof String str && !str.isBlank()) {
            return str;
        }
        return evalOutput.getFocusPoint();
    }

    private String resolveQuestionFamilyId(EvaluationDecisionOutput evalOutput) {
        if (evalOutput.getTags() != null && evalOutput.getTags().getQuestionFamilyHint() != null
                && !evalOutput.getTags().getQuestionFamilyHint().isBlank()) {
            return evalOutput.getTags().getQuestionFamilyHint();
        }
        if (evalOutput.getNextDomainCode() != null && evalOutput.getFocusPoint() != null) {
            return evalOutput.getNextDomainCode() + "." + evalOutput.getFocusPoint();
        }
        return evalOutput.getFocusPoint();
    }

    private boolean isIntro(String questionType) {
        return "INTRO".equalsIgnoreCase(asString(questionType));
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "PRINCIPLE";
        }
        return questionType.trim().toUpperCase();
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void logReductionDebug(Long sessionId,
                                   InterviewQuestion currentQuestion,
                                   LedgerMutation mutation,
                                   Map<String, Object> diff,
                                   Map<String, Object> oldLedger,
                                   Map<String, Object> newLedger,
                                   String attemptId) {
        if (!interviewDebugEnabled) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("attemptId", attemptId);
        payload.put("questionId", currentQuestion != null ? currentQuestion.getId() : null);
        payload.put("decision", mutation.getDecision());
        payload.put("domainCode", mutation.getCurrentDomainCode());
        payload.put("domainOutcome", mutation.getDomainOutcome());
        payload.put("focusPoint", mutation.getFocusPoint());
        payload.put("questionFamilyId", mutation.getQuestionFamilyId());
        payload.put("ledgerBefore", summarizeLedger(oldLedger));
        payload.put("ledgerAfter", summarizeLedger(newLedger));
        payload.put("diff", diff);
        log.info("[INTERVIEW-DEBUG][ledger.patch] {}", clipDebugText(String.valueOf(payload)));
    }

    private Map<String, Object> summarizeLedger(Map<String, Object> ledger) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (ledger == null) {
            return summary;
        }
        summary.put("activeProjectId", ledger.get("active_project_id"));
        summary.put("currentFocus", ledger.get("current_focus"));
        summary.put("coveredDomains", ledger.get("covered_domains"));
        summary.put("coveredPoints", ledger.get("covered_points"));
        summary.put("weakSignals", ledger.get("weak_signals"));
        summary.put("recentQuestionFamilies", ledger.get("recent_question_families"));
        summary.put("remainingTurnBudget", ledger.get("remaining_turn_budget"));
        return summary;
    }

    private String clipDebugText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (interviewDebugMaxTextChars <= 0 || text.length() <= interviewDebugMaxTextChars) {
            return text;
        }
        return text.substring(0, interviewDebugMaxTextChars) + "...(truncated)";
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
