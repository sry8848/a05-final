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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
                .passCurrentLevel(evalOutput.isPassCurrentLevel())
                .deepen(evalOutput.isDeepen())
                .signal(evalOutput.getSignal())
                .activeProjectId(resolveActiveProjectId(session, currentQuestion, evalOutput))
                .skipCurrentQuestion("[skip]".equals(answerText))
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
        String signal = asString(mutation.getSignal()).trim().toUpperCase(Locale.ROOT);
        if ("RETRY_SAME_DOMAIN".equals(signal) && mutation.isPassCurrentLevel()) {
            throw new IllegalArgumentException("RETRY_SAME_DOMAIN 不允许 passCurrentLevel=true");
        }
    }

    private String resolveDomainClosureReason(InterviewQuestion currentQuestion, LedgerMutation mutation) {
        if (isIntro(currentQuestion.getQuestionType())) {
            return null;
        }
        String signal = asString(mutation.getSignal()).trim().toUpperCase(Locale.ROOT);
        if (mutation.isSkipCurrentQuestion()) {
            return "NO_FURTHER_VALUE";
        }
        if ("END".equals(signal)) {
            return "NO_FURTHER_VALUE";
        }
        if ("NEXT_DOMAIN".equals(signal) && !mutation.isPassCurrentLevel()) {
            return "FAILED_HARD";
        }
        if ("NEXT_DOMAIN".equals(signal) && mutation.isPassCurrentLevel()) {
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
        existing.setStatus(Boolean.TRUE.equals(domainState.get("saturated"))
                ? DomainStatus.COVERED.getSkillStateValue()
                : domainStatus.getSkillStateValue());
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
        if (!isIntro(currentQuestion.getQuestionType())) {
            return null;
        }
        Object existing = session.getStateLedgerJson() != null ? session.getStateLedgerJson().get("active_project_id") : null;
        if (existing instanceof String projectId && !projectId.isBlank()) {
            return projectId;
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

    private boolean isIntro(String questionType) {
        return "INTRO".equalsIgnoreCase(asString(questionType));
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "PRINCIPLE";
        }
        return questionType.trim().toUpperCase(Locale.ROOT);
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
