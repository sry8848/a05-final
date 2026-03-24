package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.common.enums.DomainStatus;
import com.a05.aiinterview.interview.debug.InterviewDebugTraceService;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.a05.aiinterview.interview.entity.InterviewSession;
import com.a05.aiinterview.interview.entity.SessionSkillState;
import com.a05.aiinterview.interview.mapper.InterviewSessionMapper;
import com.a05.aiinterview.interview.mapper.SessionSkillStateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StateLedgerPatchService skill state sync tests")
class StateLedgerPatchServiceSkillStateSyncTest {

    @Test
    @DisplayName("applyReduction should sync covered domain for project question when AI closes the domain")
    void applyReduction_shouldSyncCoveredDomainForProjectQuestion() {
        InterviewSessionMapper sessionMapper = mock(InterviewSessionMapper.class);
        SessionSkillStateMapper skillStateMapper = mock(SessionSkillStateMapper.class);
        StateLedgerPatchService service = new StateLedgerPatchService(
                sessionMapper,
                skillStateMapper,
                new DefaultStateLedgerReducer(),
                new StateLedgerDiffService(),
                new InterviewDebugTraceService(new ObjectMapper())
        );

        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setStateLedgerJson(baseLedger());
        session.setSyllabusJson(Map.of(
                "domains", List.of(
                        Map.of(
                                "domainId", 6L,
                                "domainCode", "redis",
                                "domainName", "Redis"
                        )
                )
        ));
        when(sessionMapper.selectForUpdate(1L)).thenReturn(session);

        SessionSkillState existing = new SessionSkillState();
        existing.setId(99L);
        existing.setSessionId(1L);
        existing.setDomainId(6L);
        existing.setStatus(DomainStatus.UNASKED.getSkillStateValue());
        existing.setTestedCount(0);
        existing.setSaturated(false);
        existing.setEvidenceRefs(List.of());
        when(skillStateMapper.selectOne(any())).thenReturn(existing);

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setId(22L);
        currentQuestion.setQuestionType("PROJECT_DEEP_DIVE");
        currentQuestion.setGenerationContextJson(Map.of(
                "activeItemKey", "project-order",
                "activeItemType", "PROJECT",
                "activeItemName", "订单系统",
                "focusPoint", "缓存重建"
        ));

        List<EvaluationDecisionOutput.CoveredDomain> coveredDomains = List.of(
                EvaluationDecisionOutput.CoveredDomain.builder()
                        .domainCode("redis")
                        .domainName("Redis")
                        .build()
        );

        EvaluationDecisionOutput evalOutput = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision("S_ENTER_SCENARIO")
                .nextFocus("缓存击穿")
                .newCoveredDomains(coveredDomains)
                .newCoveredPoints(List.of("Redis / 缓存击穿基础方案"))
                .retrievalPlans(List.of())
                .build();

        DecisionExecutionPlan executionPlan = DecisionExecutionPlan.builder()
                .interviewAction("CONTINUE")
                .strategyCode("S_ENTER_SCENARIO")
                .targetQuestionType("SCENARIO")
                .nextFocus("缓存击穿")
                .targetDomainCode("")
                .targetDomainName("")
                .newCoveredDomains(coveredDomains)
                .newCoveredPoints(List.of("Redis / 缓存击穿基础方案"))
                .retrievalPlans(List.of())
                .build();

        service.applyReduction(1L, evalOutput, executionPlan, currentQuestion, "attempt-1", 22L, "回答");

        ArgumentCaptor<SessionSkillState> stateCaptor = ArgumentCaptor.forClass(SessionSkillState.class);
        verify(skillStateMapper).updateById(stateCaptor.capture());
        SessionSkillState updated = stateCaptor.getValue();

        assertThat(updated.getStatus()).isEqualTo(DomainStatus.COVERED.getSkillStateValue());
        assertThat(updated.getSaturated()).isTrue();
        assertThat(updated.getTestedCount()).isEqualTo(1);
        assertThat(updated.getEvidenceRefs()).containsExactly(22L);

        ArgumentCaptor<InterviewSession> sessionCaptor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionMapper).updateById(sessionCaptor.capture());
        Map<String, Object> newLedger = sessionCaptor.getValue().getStateLedgerJson();
        assertThat(newLedger.get("covered_points")).isEqualTo(List.of("Redis / 缓存击穿基础方案"));
        assertThat(newLedger.get("covered_domains")).isEqualTo(List.of(Map.of(
                "domainCode", "redis",
                "domainName", "Redis"
        )));
    }

    private Map<String, Object> baseLedger() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("overall_status", "IN_PROGRESS");
        ledger.put("asked_total", 0);
        ledger.put("last_attempt_id", null);
        ledger.put("active_item_key", null);
        ledger.put("active_item_type", null);
        ledger.put("active_item_name", null);
        ledger.put("current_focus", null);
        ledger.put("covered_domains", List.of());
        ledger.put("covered_points", List.of());
        ledger.put("recent_question_families", List.of());
        ledger.put("quota_state", new LinkedHashMap<>(Map.of(
                "samePointContinue", 0,
                "sameDomainContinue", 0,
                "sameProjectPointContinue", 0,
                "sameProjectContinue", 0,
                "principleTotal", 0,
                "projectTotal", 1,
                "scenarioTotal", 0,
                "behavioralTotal", 0
        )));
        ledger.put("decision_fallback_state", new LinkedHashMap<>(Map.of(
                "rotationIndex", 0,
                "consecutiveFallbackCount", 0,
                "totalFallbackCount", 0
        )));
        ledger.put("domain_states", List.of(
                new LinkedHashMap<>(Map.of(
                        "domainId", 6L,
                        "domainCode", "redis",
                        "domainName", "Redis",
                        "status", "UNASKED",
                        "saturated", false,
                        "evidenceRefs", List.of()
                ))
        ));
        return ledger;
    }
}
