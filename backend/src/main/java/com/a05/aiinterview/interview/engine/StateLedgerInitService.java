package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.entity.SessionSkillState;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 状态账本初始化服务。
 * 在 Planner 生成考纲后，负责：
 * 1. 根据考纲中的 domains 批量创建 session_skill_states 记录（每知识域一条）
 * 2. 构建并返回初始状态账本 JSON（写入 interview_sessions.state_ledger_json）
 *
 * <p>状态账本格式参见 面试流程策略.md §4。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StateLedgerInitService {

    private final SessionSkillStateMapper sessionSkillStateMapper;

    /**
     * 初始化状态账本并批量创建知识域考察状态记录。
     *
     * @param sessionId     面试会话 ID
     * @param plannerOutput Planner 生成的考纲
     * @param allDomains    该岗位全部知识域实体（用于补全未在考纲中出现的域）
     * @return 初始化完成的状态账本 Map（待写入 state_ledger_json）
     */
    public Map<String, Object> initLedger(Long sessionId, PlannerOutput plannerOutput,
                                          List<PositionSkillDomain> allDomains) {
        log.info("初始化状态账本, sessionId={}, 考纲知识域数={}", sessionId,
                plannerOutput.getDomains() != null ? plannerOutput.getDomains().size() : 0);

        // 将考纲 domains 按 domainId 建立 index，便于快速查找目标深度
        Map<Long, PlannerOutput.DomainPlan> planMap = new HashMap<>();
        if (plannerOutput.getDomains() != null) {
            plannerOutput.getDomains().forEach(p -> planMap.put(p.getDomainId(), p));
        }

        // 批量构建 SessionSkillState 并插入数据库
        List<SessionSkillState> states = allDomains.stream().map(domain -> {
            SessionSkillState state = new SessionSkillState();
            state.setSessionId(sessionId);
            state.setDomainId(domain.getId());
            state.setStatus(DomainStatus.UNASKED.getSkillStateValue());
            state.setTestedCount(0);
            state.setSaturated(false);
            state.setEvidenceRefs(new ArrayList<>());

            // 若考纲中有该域的规划，使用规划深度；否则默认 L3
            PlannerOutput.DomainPlan plan = planMap.get(domain.getId());
            state.setTargetDepth(plan != null ? plan.getTargetDepth() : "L3");
            state.setCurrentDepth(null);

            state.setCreatedAt(LocalDateTime.now());
            state.setUpdatedAt(LocalDateTime.now());
            return state;
        }).collect(Collectors.toList());

        // 逐条插入（幂等容错：若并发重复初始化导致唯一键冲突，忽略该条并继续）
        for (SessionSkillState state : states) {
            try {
                sessionSkillStateMapper.insert(state);
            } catch (DuplicateKeyException ex) {
                log.warn("session_skill_states 已存在，忽略重复初始化, sessionId={}, domainId={}",
                        sessionId, state.getDomainId());
            }
        }

        log.info("session_skill_states 批量创建完成, sessionId={}, 共 {} 条", sessionId, states.size());

        // 构建状态账本 JSON
        return buildLedger(sessionId, plannerOutput);
    }

    /**
     * 构建初始状态账本 JSON。
     * 格式对齐 面试流程策略.md §4。
     */
    private Map<String, Object> buildLedger(Long sessionId, PlannerOutput plannerOutput) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("session_id", sessionId.toString());
        ledger.put("overall_status", DomainStatus.IN_PROGRESS.getValue());
        ledger.put("active_project_id", null);
        ledger.put("current_focus", null);
        ledger.put("remaining_turn_budget", initialTurnBudget(plannerOutput));
        ledger.put("covered_domains", new ArrayList<>());
        ledger.put("covered_points", new ArrayList<>());
        ledger.put("weak_signals", new ArrayList<>());
        ledger.put("recent_question_families", new ArrayList<>());
        ledger.put("rescue_total", 0);
        ledger.put("rescue_counts_by_domain", new LinkedHashMap<>());
        ledger.put("last_focus_point", null);
        ledger.put("current_focus_streak", 0);
        // 新增：当前知识域连续追问计数，用于限制同一域的追问轮数
        ledger.put("current_domain_code", null);
        ledger.put("current_domain_followup_count", 0);

        // 各知识域初始状态
        //TODO 知识域描述过于简单随意
        List<Map<String, Object>> domainStates = new ArrayList<>();
        if (plannerOutput.getDomains() != null) {
            plannerOutput.getDomains().forEach(d -> {
                Map<String, Object> ds = new LinkedHashMap<>();
                ds.put("domain_id", d.getDomainCode());
                ds.put("target_depth", d.getTargetDepth() != null ? d.getTargetDepth() : "L3");
                ds.put("current_depth", null);
                ds.put("status", DomainStatus.UNASKED.getValue());
                ds.put("saturated", false);
                ds.put("evidence_refs", new ArrayList<>());
                domainStates.add(ds);
            });
        }
        ledger.put("domain_states", domainStates);

        // 题型进度初始化为 0
        Map<String, Integer> mixProgress = new LinkedHashMap<>();
        if (plannerOutput.getQuestionMixPlan() != null) {
            plannerOutput.getQuestionMixPlan().keySet().forEach(k -> mixProgress.put(k, 0));
        }
        ledger.put("question_mix_progress", mixProgress);

        ledger.put("asked_total", 0);
        ledger.put("last_attempt_id", null);

        return ledger;
    }

    private int initialTurnBudget(PlannerOutput plannerOutput) {
        if (plannerOutput.getQuestionMixPlan() == null || plannerOutput.getQuestionMixPlan().isEmpty()) {
            return 0;
        }
        return plannerOutput.getQuestionMixPlan().values().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }
}
