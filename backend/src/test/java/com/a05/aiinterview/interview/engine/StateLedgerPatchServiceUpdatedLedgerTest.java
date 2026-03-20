package com.a05.aiinterview.interview.engine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateLedgerReducerTest {

    private final StateLedgerReducer reducer = new DefaultStateLedgerReducer();

    @Test
    void reduce_intro_shouldUpdateGlobalStateOnly() {
        Map<String, Object> oldLedger = baseLedger("java_core");

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("INTRO")
                .decision("probe")
                .answerVerdict("STRONG")
                .activeProjectId("p_001")
                .currentFocus("订单项目")
                .statePatch(Map.of(
                        "activeProjectId", "p_001",
                        "currentFocus", "订单项目"
                ))
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-intro-1", 100L);

        assertThat(newLedger.get("asked_total")).isEqualTo(1);
        assertThat(newLedger.get("last_attempt_id")).isEqualTo("attempt-intro-1");
        assertThat(newLedger.get("active_project_id")).isEqualTo("p_001");
        assertThat(newLedger.get("current_focus")).isEqualTo("订单项目");
        @SuppressWarnings("unchecked")
        Map<String, Object> mixProgress = (Map<String, Object>) newLedger.get("question_mix_progress");
        assertThat(mixProgress).containsEntry("INTRO", 1);
        assertThat(newLedger.get("domain_states")).isEqualTo(oldLedger.get("domain_states"));
    }

    @Test
    void reduce_introRescueShouldCountTowardsRescueQuota() {
        Map<String, Object> oldLedger = baseLedger("java_core");

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("INTRO")
                .decision("rescue")
                .answerVerdict("PARTIAL")
                .currentDomainCode("intro")
                .focusPoint("项目真实性")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-intro-rescue-1", 101L);

        assertThat(newLedger.get("rescue_total")).isEqualTo(1);
        assertThat(newLedger.get("rescue_counts_by_domain")).isEqualTo(Map.of("intro", 1));
    }

    @Test
    void reduce_weakBroadenShouldCircuitBreakCurrentDomain() {
        Map<String, Object> oldLedger = baseLedger("redis");

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentTargetDepth("L2")
                .decision("broaden")
                .answerVerdict("WEAK")
                .domainOutcome("circuit_broken")
                .focusPoint("缓存击穿")
                .statePatch(Map.of(
                        "weakSignalsAdd", List.of("缓存击穿不清晰")
                ))
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-1", 22L);

        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(redis.get("status")).isEqualTo("CIRCUIT_BROKEN");
        assertThat(redis.get("saturated")).isEqualTo(true);
        assertThat(redis.get("evidence_refs")).isEqualTo(List.of(22L));
        assertThat(newLedger.get("weak_signals")).isEqualTo(List.of("缓存击穿不清晰"));
    }

    @Test
    void reduce_partialRescueShouldKeepDomainInProgress() {
        Map<String, Object> oldLedger = baseLedger("redis");

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentTargetDepth("L2")
                .decision("rescue")
                .answerVerdict("PARTIAL")
                .domainOutcome("continue")
                .focusPoint("缓存击穿")
                .statePatch(Map.of(
                        "currentFocus", "缓存击穿",
                        "weakSignalsAdd", List.of("实现思路不清晰")
                ))
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-1b", 23L);

        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(redis.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(redis.get("current_depth")).isEqualTo("");
        assertThat(redis.get("saturated")).isEqualTo(false);
        assertThat(redis.get("evidence_refs")).isEqualTo(List.of(23L));
        assertThat(newLedger.get("current_focus")).isEqualTo("缓存击穿");
        assertThat(newLedger.get("rescue_total")).isEqualTo(1);
        assertThat(newLedger.get("rescue_counts_by_domain")).isEqualTo(Map.of("redis", 1));
        assertThat(newLedger.get("last_focus_point")).isEqualTo("缓存击穿");
        assertThat(newLedger.get("current_focus_streak")).isEqualTo(1);
    }

    @Test
    void reduce_strongCoveredShouldAdvanceDepthAndMarkCovered() {
        Map<String, Object> oldLedger = baseLedger("mq");
        @SuppressWarnings("unchecked")
        Map<String, Object> mq = (Map<String, Object>) ((List<?>) oldLedger.get("domain_states")).getFirst();
        mq.put("status", "IN_PROGRESS");
        mq.put("current_depth", "L1");
        mq.put("evidence_refs", List.of(10L));

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PROJECT_DEEP_DIVE")
                .currentDomainCode("mq")
                .currentDomainId(8L)
                .currentTargetDepth("L2")
                .decision("probe")
                .answerVerdict("STRONG")
                .domainOutcome("covered")
                .focusPoint("支付回调并发冲突")
                .statePatch(Map.of(
                        "coveredPointsAdd", List.of("mq:pay-close-conflict"),
                        "recentQuestionFamiliesAdd", List.of("mq.pay-close.boundary")
                ))
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-mq-2", 33L);

        @SuppressWarnings("unchecked")
        Map<String, Object> newMq = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(newMq.get("status")).isEqualTo("COVERED");
        assertThat(newMq.get("current_depth")).isEqualTo("L2");
        assertThat(newMq.get("saturated")).isEqualTo(true);
        assertThat(newMq.get("evidence_refs")).isEqualTo(List.of(10L, 33L));
        assertThat(newLedger.get("covered_points")).isEqualTo(List.of("mq:pay-close-conflict"));
        assertThat(newLedger.get("recent_question_families")).isEqualTo(List.of("mq.pay-close.boundary"));
        assertThat(newLedger.get("last_focus_point")).isEqualTo("支付回调并发冲突");
        assertThat(newLedger.get("current_focus_streak")).isEqualTo(1);
    }

    @Test
    void reduce_sameFocusFollowupShouldIncreaseFocusStreak() {
        Map<String, Object> oldLedger = baseLedger("redis");
        oldLedger.put("last_focus_point", "缓存击穿");
        oldLedger.put("current_focus_streak", 4);

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentTargetDepth("L2")
                .decision("followup")
                .answerVerdict("STRONG")
                .domainOutcome("continue")
                .focusPoint("缓存击穿")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-2", 24L);

        assertThat(newLedger.get("last_focus_point")).isEqualTo("缓存击穿");
        assertThat(newLedger.get("current_focus_streak")).isEqualTo(5);
    }

    @Test
    void reduce_coveredDomainShouldNotBeReopened() {
        Map<String, Object> oldLedger = baseLedger("mq");
        @SuppressWarnings("unchecked")
        Map<String, Object> mq = (Map<String, Object>) ((List<?>) oldLedger.get("domain_states")).getFirst();
        mq.put("status", "COVERED");
        mq.put("current_depth", "L2");
        mq.put("saturated", true);
        mq.put("evidence_refs", List.of(10L));

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PROJECT_DEEP_DIVE")
                .currentDomainCode("mq")
                .currentDomainId(8L)
                .currentTargetDepth("L3")
                .decision("followup")
                .answerVerdict("PARTIAL")
                .domainOutcome("continue")
                .focusPoint("支付回调并发冲突")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-mq-3", 34L);

        @SuppressWarnings("unchecked")
        Map<String, Object> newMq = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(newMq.get("status")).isEqualTo("COVERED");
        assertThat(newMq.get("current_depth")).isEqualTo("L2");
        assertThat(newMq.get("saturated")).isEqualTo(true);
        assertThat(newMq.get("evidence_refs")).isEqualTo(List.of(10L, 34L));
    }

    private Map<String, Object> baseLedger(String domainCode) {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("session_id", "26");
        ledger.put("overall_status", "IN_PROGRESS");
        ledger.put("active_project_id", null);
        ledger.put("current_focus", null);
        ledger.put("remaining_turn_budget", 8);
        ledger.put("covered_domains", List.of());
        ledger.put("covered_points", List.of());
        ledger.put("weak_signals", List.of());
        ledger.put("recent_question_families", List.of());
        ledger.put("rescue_total", 0);
        ledger.put("rescue_counts_by_domain", new LinkedHashMap<>());
        ledger.put("last_focus_point", null);
        ledger.put("current_focus_streak", 0);
        ledger.put("domain_states", List.of(
                new LinkedHashMap<>(Map.of(
                        "domain_id", domainCode,
                        "status", "UNASKED",
                        "target_depth", "L2",
                        "current_depth", "",
                        "saturated", false,
                        "evidence_refs", List.of()
                ))
        ));
        ledger.put("question_mix_progress", new LinkedHashMap<>(Map.of("INTRO", 0, "PRINCIPLE", 0, "PROJECT_DEEP_DIVE", 0)));
        ledger.put("asked_total", 0);
        ledger.put("last_attempt_id", null);
        return ledger;
    }
}
