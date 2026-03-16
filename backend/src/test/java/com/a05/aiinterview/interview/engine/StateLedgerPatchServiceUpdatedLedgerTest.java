package com.a05.aiinterview.interview.engine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateLedgerReducerTest {

    private final StateLedgerReducer reducer = new DefaultStateLedgerReducer();

    @Test
    void reduce_intro_shouldOnlyUpdateGlobalCounters() {
        Map<String, Object> oldLedger = new LinkedHashMap<>();
        oldLedger.put("session_id", "26");
        oldLedger.put("overall_status", "IN_PROGRESS");
        oldLedger.put("active_project_id", null);
        oldLedger.put("domain_states", List.of(
                Map.of(
                        "domain_id", "java_core",
                        "status", "UNASKED",
                        "target_depth", "L2",
                        "current_depth", "",
                        "saturated", false,
                        "evidence_refs", List.of()
                )
        ));
        oldLedger.put("question_mix_progress", new LinkedHashMap<>(Map.of("INTRO", 0, "PRINCIPLE", 0)));
        oldLedger.put("asked_total", 0);
        oldLedger.put("last_attempt_id", null);

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("INTRO")
                .passCurrentLevel(true)
                .deepen(false)
                .signal("NEXT_DOMAIN")
                .activeProjectId("p_001")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-intro-1", 100L);

        assertThat(newLedger.get("asked_total")).isEqualTo(1);
        assertThat(newLedger.get("last_attempt_id")).isEqualTo("attempt-intro-1");
        assertThat(newLedger.get("active_project_id")).isEqualTo("p_001");
        @SuppressWarnings("unchecked")
        Map<String, Object> mixProgress = (Map<String, Object>) newLedger.get("question_mix_progress");
        assertThat(mixProgress).containsEntry("INTRO", 1);
        assertThat(newLedger.get("domain_states")).isEqualTo(oldLedger.get("domain_states"));
    }

    @Test
    void reduce_hardFailShouldCloseCurrentDomain() {
        Map<String, Object> oldLedger = new LinkedHashMap<>();
        oldLedger.put("domain_states", List.of(
                Map.of(
                        "domain_id", "redis",
                        "status", "UNASKED",
                        "target_depth", "L2",
                        "current_depth", "",
                        "saturated", false,
                        "evidence_refs", List.of()
                )
        ));
        oldLedger.put("question_mix_progress", new LinkedHashMap<>(Map.of("PRINCIPLE", 0)));
        oldLedger.put("asked_total", 0);
        oldLedger.put("last_attempt_id", null);

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentTargetDepth("L2")
                .passCurrentLevel(false)
                .deepen(false)
                .signal("NEXT_DOMAIN")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-1", 22L);

        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(redis.get("status")).isEqualTo("COVERED");
        assertThat(redis.get("current_depth")).isEqualTo("");
        assertThat(redis.get("saturated")).isEqualTo(true);
        assertThat(redis.get("evidence_refs")).isEqualTo(List.of(22L));
    }

    @Test
    void reduce_partialFailShouldKeepDomainInProgressWithoutAdvancingDepth() {
        Map<String, Object> oldLedger = new LinkedHashMap<>();
        oldLedger.put("domain_states", List.of(
                Map.of(
                        "domain_id", "redis",
                        "status", "UNASKED",
                        "target_depth", "L2",
                        "current_depth", "",
                        "saturated", false,
                        "evidence_refs", List.of()
                )
        ));
        oldLedger.put("question_mix_progress", new LinkedHashMap<>(Map.of("PRINCIPLE", 0)));
        oldLedger.put("asked_total", 0);
        oldLedger.put("last_attempt_id", null);

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentTargetDepth("L2")
                .passCurrentLevel(false)
                .deepen(false)
                .signal("RETRY_SAME_DOMAIN")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-1b", 23L);

        @SuppressWarnings("unchecked")
        Map<String, Object> redis = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(redis.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(redis.get("current_depth")).isEqualTo("");
        assertThat(redis.get("saturated")).isEqualTo(false);
        assertThat(redis.get("evidence_refs")).isEqualTo(List.of(23L));
    }

    @Test
    void reduce_passedLevelWithoutDeepen_shouldCoverCurrentDomain() {
        Map<String, Object> oldLedger = new LinkedHashMap<>();
        oldLedger.put("domain_states", List.of(
                Map.of(
                        "domain_id", "mq",
                        "status", "IN_PROGRESS",
                        "target_depth", "L3",
                        "current_depth", "L1",
                        "saturated", false,
                        "evidence_refs", List.of(10L)
                )
        ));
        oldLedger.put("question_mix_progress", new LinkedHashMap<>(Map.of("PROJECT_DEEP_DIVE", 0)));
        oldLedger.put("asked_total", 1);
        oldLedger.put("last_attempt_id", "attempt-old");

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PROJECT_DEEP_DIVE")
                .currentDomainCode("mq")
                .currentDomainId(8L)
                .currentTargetDepth("L2")
                .passCurrentLevel(true)
                .deepen(false)
                .signal("NEXT_DOMAIN")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-mq-2", 33L);

        @SuppressWarnings("unchecked")
        Map<String, Object> mq = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(mq.get("status")).isEqualTo("COVERED");
        assertThat(mq.get("current_depth")).isEqualTo("L2");
        assertThat(mq.get("saturated")).isEqualTo(true);
        assertThat(mq.get("evidence_refs")).isEqualTo(List.of(10L, 33L));
        assertThat(newLedger.get("asked_total")).isEqualTo(2);
        assertThat(newLedger.get("last_attempt_id")).isEqualTo("attempt-mq-2");
    }

    @Test
    void reduce_coveredDomainShouldNotBeReopened() {
        Map<String, Object> oldLedger = new LinkedHashMap<>();
        oldLedger.put("domain_states", List.of(
                Map.of(
                        "domain_id", "mq",
                        "status", "COVERED",
                        "target_depth", "L3",
                        "current_depth", "L2",
                        "saturated", true,
                        "evidence_refs", List.of(10L)
                )
        ));
        oldLedger.put("question_mix_progress", new LinkedHashMap<>(Map.of("PROJECT_DEEP_DIVE", 0)));
        oldLedger.put("asked_total", 1);
        oldLedger.put("last_attempt_id", "attempt-old");

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PROJECT_DEEP_DIVE")
                .currentDomainCode("mq")
                .currentDomainId(8L)
                .currentTargetDepth("L3")
                .passCurrentLevel(false)
                .deepen(false)
                .signal("RETRY_SAME_DOMAIN")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-mq-3", 34L);

        @SuppressWarnings("unchecked")
        Map<String, Object> mq = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(mq.get("status")).isEqualTo("COVERED");
        assertThat(mq.get("current_depth")).isEqualTo("L2");
        assertThat(mq.get("saturated")).isEqualTo(true);
        assertThat(mq.get("evidence_refs")).isEqualTo(List.of(10L, 34L));
    }
}
