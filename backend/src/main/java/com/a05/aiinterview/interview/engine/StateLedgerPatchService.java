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
 * 状态账本写入服务（State Ledger Patch Service）。
 *
 * <p>核心设计思想（Command/Query Responsibility Segregation 命令查询分离）：
 * <ul>
 *   <li>不直接修改旧账本，而是构建结构化的 LedgerMutation（变更指令）</li>
 *   <li>将 mutation 交给 StateLedgerReducer 计算新账本（不可变更新）</li>
 *   <li>新账本写入数据库，并同步更新关系型表 session_skill_states</li>
 * </ul>
 *
 * <p>关键特性：
 * <ul>
 *   <li>使用 selectForUpdate 进行乐观锁，防止并发更新冲突</li>
 *   <li>双持久化：JSON 账本 + 关系型表，兼顾性能和查询便利</li>
 *   <li>生成差异对比（diff），用于审计和调试</li>
 * </ul>
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

    /**
     * 应用状态账本变更（Reduction）的主方法。
     *
     * <p>处理流程（事务内原子操作）：
     * <ol>
     *   <li>selectForUpdate 锁定会话，防止并发更新</li>
     *   <li>规范化旧账本（补全必要字段）</li>
     *   <li>构建 LedgerMutation 变更指令</li>
     *   <li>调用 reducer 计算新账本（深拷贝旧账本 + 应用变更）</li>
     *   <li>应用兜底状态更新</li>
     *   <li>生成新旧账本差异对比</li>
     *   <li>将新账本写回 interview_sessions.state_ledger_json</li>
     *   <li>同步更新 session_skill_states 关系表</li>
     *   <li>记录调试日志</li>
     *   <li>返回审计信息</li>
     * </ol>
     *
     * @param sessionId 会话 ID
     * @param evalOutput AI 评估输出
     * @param executionPlan 决策执行计划
     * @param currentQuestion 当前题目
     * @param attemptId 作答记录 ID
     * @param evidenceQuestionId 作为证据的题目 ID
     * @param answerText 用户回答文本
     * @return 变更审计信息，包含新账本、差异、知识域关闭原因
     */
    @Transactional(rollbackFor = Exception.class)
    public ReductionAudit applyReduction(Long sessionId,
                                         EvaluationDecisionOutput evalOutput,
                                         DecisionExecutionPlan executionPlan,
                                         InterviewQuestion currentQuestion,
                                         String attemptId,
                                         Long evidenceQuestionId,
                                         String answerText) {
        // 步骤1：加锁查询会话，防止并发更新（使用 select for update）
        InterviewSession session = interviewSessionMapper.selectForUpdate(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在, sessionId=" + sessionId);
        }
        if (session.getStateLedgerJson() == null) {
            throw new IllegalStateException("状态账本为空, sessionId=" + sessionId);
        }

        // 步骤2：规范化旧账本（补全 asked_total 等必要字段）
        Map<String, Object> normalizedLedger = normalizeLedgerForReduction(session);

        // 步骤3：构建 LedgerMutation 变更指令（结构化变更输入）
        LedgerMutation mutation = buildMutation(session, currentQuestion, evalOutput, executionPlan, answerText);

        // 步骤4：调用 reducer 计算新账本（深拷贝 + 应用变更）
        Map<String, Object> newLedger = stateLedgerReducer.reduce(
                normalizedLedger, mutation, attemptId, evidenceQuestionId);

        // 步骤5：应用兜底状态更新（更新 fallback 索引等）
        DecisionFallbackStateSupport.applyPlan(newLedger, executionPlan);

        // 步骤6：生成新旧账本差异对比（用于调试和审计）
        Map<String, Object> diff = stateLedgerDiffService.diff(normalizedLedger, newLedger);

        // 步骤7：将新账本写回数据库
        InterviewSession update = new InterviewSession();
        update.setId(sessionId);
        update.setStateLedgerJson(newLedger);
        update.setUpdatedAt(LocalDateTime.now());
        interviewSessionMapper.updateById(update);

        // 步骤8：同步更新 session_skill_states 关系表（知识域状态）
        syncSkillState(sessionId, mutation, newLedger, evidenceQuestionId);

        // 步骤9：记录调试日志
        logReductionDebug(sessionId, currentQuestion, mutation, diff, normalizedLedger, newLedger, attemptId);

        // 步骤10：返回审计信息
        return ReductionAudit.builder()
                .newLedger(newLedger)
                .diff(diff)
                .domainClosureReason(resolveDomainClosureReason(mutation))
                .build();
    }

    /**
     * 构建 LedgerMutation 变更指令对象。
     *
     * <p>变更指令（LedgerMutation）是状态账本更新的核心输入，它将 AI 决策结果转换为结构化的变更描述。
     *
     * <p>优先级规则：优先使用 executionPlan（验证/修复后的计划），其次使用 evalOutput（AI 原始输出）
     *
     * <p>构建内容包括：
     * <ul>
     *   <li>当前题目的基本信息（题型、知识域、焦点等
     *   <li>下一题的焦点和激活项
     *   <li>新覆盖的知识域和知识点
     *   <li>面试动作（继续/结束）
     * </ul>
     *
     * @param session 面试会话对象
     * @param currentQuestion 当前正在作答的题目
     * @param evalOutput AI 评估决策的原始输出
     * @param executionPlan 经过验证/修复后的最终执行计划
     * @param answerText 用户回答文本，用于判断是否跳过
     * @return 结构化的 LedgerMutation 变更指令对象
     */
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
                currentQuestion.getFocusPoint(),
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

    /**
     * 解析知识域关闭原因（用于调试和审计）。
     *
     * @param mutation 变更指令
     * @return 关闭原因：COVERED/SKIPPED/WRAPUP 或 null
     */
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

    /**
     * 同步更新 session_skill_states 关系表。
     *
     * <p>需要同步的知识域：
     * <ul>
     *   <li>当前知识域（mutation.getCurrentDomainCode）</li>
     *   <li>新覆盖的知识域（mutation.getNewCoveredDomains）</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param mutation 变更指令
     * @param newLedger 新账本
     * @param evidenceQuestionId 证据题目 ID
     */
    private void syncSkillState(Long sessionId,
                                LedgerMutation mutation,
                                Map<String, Object> newLedger,
                                Long evidenceQuestionId) {
        for (String domainCode : collectSkillStateSyncDomainCodes(mutation)) {
            syncSingleSkillState(sessionId, domainCode, newLedger, evidenceQuestionId);
        }
    }

    /**
     * 同步更新单个知识域的 session_skill_states 记录。
     *
     * <p>更新内容：
     * <ul>
     *   <li>status：知识域状态（unasked/in_progress/covered）</li>
     *   <li>saturated：是否问透</li>
     *   <li>testedCount：已考察题数</li>
     *   <li>evidenceRefs：关联的题目ID列表</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param domainCode 知识域编码
     * @param newLedger 新账本
     * @param evidenceQuestionId 证据题目 ID
     */
    private void syncSingleSkillState(Long sessionId,
                                      String domainCode,
                                      Map<String, Object> newLedger,
                                      Long evidenceQuestionId) {
        if (domainCode == null || domainCode.isBlank()) {
            return;
        }
        SessionSkillState existing = sessionSkillStateMapper.selectOne(
                new LambdaQueryWrapper<SessionSkillState>()
                        .eq(SessionSkillState::getSessionId, sessionId)
                        .eq(SessionSkillState::getDomainCode, domainCode));
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

    /**
     * 收集需要同步的知识域编码列表（去重）。
     *
     * @param mutation 变更指令
     * @return 需要同步的知识域编码列表
     */
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

    /**
     * 规范化旧账本，补全必要字段，确保 Reducer 能正常工作。
     *
     * <p>规范化内容：
     * <ul>
     *   <li>补全 `asked_total` 字段（已答题数）</li>
     *   <li>优先使用 `session.currentQuestionNo`，其次使用账本中的值</li>
     * </ul>
     *
     * <p>为什么需要规范化？
     * <ul>
     *   <li>旧版本数据可能缺少某些新字段</li>
     *   <li>确保 Reducer 处理时不会因字段缺失而出错</li>
     *   <li>统一数据来源，避免不一致</li>
     * </ul>
     *
     * @param session 面试会话对象
     * @return 规范化后的账本副本（不会修改原始账本）
     */
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
        if (question != null && question.getDomainCode() != null && !question.getDomainCode().isBlank()) {
            return question.getDomainCode();
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
