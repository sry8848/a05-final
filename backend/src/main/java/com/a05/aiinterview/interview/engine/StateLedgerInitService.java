package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.dto.InterviewSyllabus;
import com.a05.aiinterview.interview.entity.SessionSkillState;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.a05.aiinterview.position.entity.PositionSkillDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Map<String, Object> initLedger(Long sessionId, InterviewSyllabus syllabus,
                                          List<PositionSkillDomain> allDomains) {
        log.info("初始化状态账本, sessionId={}, 考纲知识域数={}", sessionId,
                syllabus.getDomains() != null ? syllabus.getDomains().size() : 0);

        // 批量构建 SessionSkillState 并插入数据库
        List<SessionSkillState> states = syllabus.getDomains().stream().map(domain -> {
            SessionSkillState state = new SessionSkillState();
            state.setSessionId(sessionId);
            state.setDomainId(domain.getDomainId());
            state.setStatus(DomainStatus.UNASKED.getSkillStateValue());
            state.setTestedCount(0);
            state.setSaturated(false);
            state.setEvidenceRefs(new ArrayList<>());
            state.setCreatedAt(LocalDateTime.now());
            state.setUpdatedAt(LocalDateTime.now());
            return state;
        }).toList();

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
        return buildLedger(sessionId, syllabus);
    }

    /**
     * 构建初始状态账本 JSON。
     * 格式对齐 面试流程策略.md §4。
     */
    private Map<String, Object> buildLedger(Long sessionId, InterviewSyllabus syllabus) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("session_id", sessionId.toString());
        ledger.put("overall_status", DomainStatus.IN_PROGRESS.getValue());
        ledger.put("single_question_mode", false);
        ledger.put("active_item_key", null);
        ledger.put("active_item_type", null);
        ledger.put("active_item_name", null);
        ledger.put("current_focus", null);
        ledger.put("covered_domains", new ArrayList<>());
        ledger.put("covered_points", new ArrayList<>());
        ledger.put("recent_question_families", new ArrayList<>());
        ledger.put(QuotaStateSupport.LEDGER_KEY, QuotaStateSupport.initialQuotaState());

        List<Map<String, Object>> domainStates = new ArrayList<>();
        if (syllabus.getDomains() != null) {
            syllabus.getDomains().forEach(d -> {
                Map<String, Object> ds = new LinkedHashMap<>();
                ds.put("domainId", d.getDomainId());
                ds.put("domainCode", d.getDomainCode());
                ds.put("domainName", d.getDomainName());
                ds.put("status", DomainStatus.UNASKED.getValue());
                ds.put("saturated", false);
                ds.put("evidenceRefs", new ArrayList<>());
                domainStates.add(ds);
            });
        }
        ledger.put("domain_states", domainStates);
        ledger.put("asked_total", 0);
        ledger.put("last_attempt_id", null);
        return ledger;
    }
}
