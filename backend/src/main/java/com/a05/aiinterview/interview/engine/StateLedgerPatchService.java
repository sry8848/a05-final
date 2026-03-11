package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.SessionSkillState;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 状态账本 Patch 应用服务（单一职责）。
 *
 * <p>职责：将评估决策服务返回的 {@link EvaluationDecisionOutput.LedgerPatch} 安全地写入：
 * <ol>
 *   <li>{@code interview_sessions.state_ledger_json} —— 更新 domain_states、question_mix_progress、
 *       asked_total、last_attempt_id</li>
 *   <li>{@code session_skill_states} —— 更新对应行的 current_depth、saturated、status、tested_count</li>
 * </ol>
 *
 * <p>并发安全：通过 {@code SELECT FOR UPDATE} 对同一 sessionId 的会话行加排他锁，
 * 保证同一场面试的账本 Patch 串行执行，避免并发覆写。
 *
 * <p>事务：整个方法在同一数据库事务中完成，任何子步骤失败均整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateLedgerPatchService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final SessionSkillStateMapper sessionSkillStateMapper;

    /**
     * 将评估决策 Patch 应用到账本，同时更新 session_skill_states 行。
     *
     * @param sessionId          面试会话 ID
     * @param patch              评估决策返回的账本变更描述
     * @param attemptId          本次 attempt 的幂等键（写入账本 last_attempt_id）
     * @param evidenceQuestionId 本次被回答题目的 ID（追加到账本 evidence_refs，可为 null）
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyPatch(Long sessionId,
                           EvaluationDecisionOutput.LedgerPatch patch,
                           String attemptId,
                           Long evidenceQuestionId) {
        log.info("开始应用账本 Patch, sessionId={}, domainCode={}, attemptId={}",
                sessionId, patch != null ? patch.getDomainCode() : "null", attemptId);

        // 以排他行锁加载会话，保证同 session 的 Patch 串行
        InterviewSession session = interviewSessionMapper.selectForUpdate(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }

        Map<String, Object> ledger = session.getStateLedgerJson();
        if (ledger == null) {
            throw new IllegalStateException("状态账本为空, sessionId=" + sessionId);
        }

        // 更新账本 domain_states
        if (patch != null && patch.getDomainCode() != null) {
            applyDomainStatePatch(ledger, patch, evidenceQuestionId);
        }

        // 递增 question_mix_progress 计数
        if (patch != null && patch.getQuestionType() != null) {
            incrementMixProgress(ledger, patch.getQuestionType());
        }

        // 递增 asked_total
        int askedTotal = toInt(ledger.getOrDefault("asked_total", 0)) + 1;
        ledger.put("asked_total", askedTotal);

        // 更新 last_attempt_id
        ledger.put("last_attempt_id", attemptId);

        // 写回会话
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStateLedgerJson(ledger);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);

        // 同步更新 session_skill_states 行
        if (patch != null && patch.getDomainId() != null) {
            updateSkillState(sessionId, patch);
        }

        log.info("账本 Patch 应用完成, sessionId={}, domain={}, depth={}, askedTotal={}, attemptId={}",
                sessionId, patch != null ? patch.getDomainCode() : "null",
                patch != null ? patch.getCurrentDepth() : "null",
                askedTotal, attemptId);
    }

    // ────────────────────────────────────────────────────────────
    // 私有方法
    // ────────────────────────────────────────────────────────────

    /**
     * 更新账本 domain_states 中对应域的条目。
     * 匹配规则：账本中 domain_id 字段存储的是知识域编码（domainCode）。
     */
    @SuppressWarnings("unchecked")
    private void applyDomainStatePatch(Map<String, Object> ledger,
                                       EvaluationDecisionOutput.LedgerPatch patch,
                                       Long evidenceQuestionId) {
        Object domainStatesObj = ledger.get("domain_states");
        if (!(domainStatesObj instanceof List<?> rawList)) {
            return;
        }

        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> ds = (Map<String, Object>) rawMap;
            if (!patch.getDomainCode().equals(ds.get("domain_id"))) continue;

            ds.put("current_depth", patch.getCurrentDepth());
            // 统一使用枚举 getValue() 写入账本，确保始终为 UNASKED/IN_PROGRESS/COVERED/CIRCUIT_BROKEN
            DomainStatus domainStatus = patch.getDomainStatus() != null
                    ? patch.getDomainStatus() : DomainStatus.IN_PROGRESS;
            ds.put("status", domainStatus.getValue());
            ds.put("saturated", patch.isSaturated());

            // 追加 evidence_ref（题目 ID）
            if (evidenceQuestionId != null) {
                List<Object> refs = (List<Object>) ds.computeIfAbsent("evidence_refs", k -> new ArrayList<>());
                refs.add(evidenceQuestionId);
            }
            break;
        }
    }

    /**
     * 递增 question_mix_progress 中指定题型的计数。
     */
    @SuppressWarnings("unchecked")
    private void incrementMixProgress(Map<String, Object> ledger, String questionType) {
        Object mixObj = ledger.get("question_mix_progress");
        if (!(mixObj instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> mixProgress = (Map<String, Object>) rawMap;
        int current = toInt(mixProgress.getOrDefault(questionType, 0));
        mixProgress.put(questionType, current + 1);
    }

    /**
     * 更新 session_skill_states 表对应行的覆盖状态。
     */
    private void updateSkillState(Long sessionId, EvaluationDecisionOutput.LedgerPatch patch) {
        SessionSkillState existing = sessionSkillStateMapper.selectOne(
                new LambdaQueryWrapper<SessionSkillState>()
                        .eq(SessionSkillState::getSessionId, sessionId)
                        .eq(SessionSkillState::getDomainId, patch.getDomainId())
        );

        if (existing == null) {
            log.warn("session_skill_states 未找到对应行, sessionId={}, domainId={}", sessionId, patch.getDomainId());
            return;
        }

        // 枚举直接提供关系表侧的写入值，无需字符串映射转换
        String skillStatus = resolveSkillStateValue(patch.getDomainStatus(), patch.isSaturated());

        existing.setCurrentDepth(patch.getCurrentDepth());
        existing.setSaturated(patch.isSaturated());
        existing.setStatus(skillStatus);
        existing.setTestedCount(existing.getTestedCount() + 1);
        if (patch.getEvidenceQuestionId() != null) {
            List<Long> refs = existing.getEvidenceRefs() != null
                    ? new ArrayList<>(existing.getEvidenceRefs()) : new ArrayList<>();
            refs.add(patch.getEvidenceQuestionId());
            existing.setEvidenceRefs(refs);
        }
        existing.setUpdatedAt(LocalDateTime.now());
        sessionSkillStateMapper.updateById(existing);
    }

    /**
     * 将 {@link DomainStatus} 枚举解析为 session_skill_states.status 列的写入值。
     * saturated=true 时强制写 COVERED，否则直接使用枚举的 skillStateValue。
     *
     * @param domainStatus 账本侧枚举（可为 null，兜底为 IN_PROGRESS）
     * @param saturated    是否已问透
     * @return session_skill_states.status 列应写入的字符串（小写下划线风格）
     */
    private String resolveSkillStateValue(DomainStatus domainStatus, boolean saturated) {
        if (saturated) {
            return DomainStatus.COVERED.getSkillStateValue();
        }
        DomainStatus effective = domainStatus != null ? domainStatus : DomainStatus.IN_PROGRESS;
        return effective.getSkillStateValue();
    }

    private int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return 0; }
    }
}
