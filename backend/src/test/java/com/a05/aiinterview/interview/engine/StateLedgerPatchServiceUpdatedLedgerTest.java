package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StateLedgerPatchServiceUpdatedLedgerTest {

    private final StateLedgerReducer reducer = new DefaultStateLedgerReducer();

    @Test
    void reduce_shouldUpdateFocusItemAndQuestionFamily() {
        Map<String, Object> oldLedger = baseLedger();

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("INTRO")
                .currentFocus("自我介绍")
                .nextFocus("订单项目")
                .currentItemKey("item-old")
                .nextItemKey("item-order")
                .nextItemType("PROJECT")
                .nextItemName("订单系统")
                .questionFamilyId("INTRO.订单项目")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-intro-1", 100L);

        assertThat(newLedger.get("asked_total")).isEqualTo(1);
        assertThat(newLedger.get("last_attempt_id")).isEqualTo("attempt-intro-1");
        assertThat(newLedger.get("current_focus")).isEqualTo("订单项目");
        assertThat(newLedger.get("active_item_key")).isEqualTo("item-order");
        assertThat(newLedger.get("active_item_type")).isEqualTo("PROJECT");
        assertThat(newLedger.get("active_item_name")).isEqualTo("订单系统");
        assertThat(newLedger.get("recent_question_families")).isEqualTo(List.of("INTRO.订单项目"));
    }

    @Test
    void reduce_shouldMergeCoveredDomainsCoveredPointsAndCandidatePoints() {
        Map<String, Object> oldLedger = baseLedger();

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentFocus("缓存击穿")
                .questionFamilyId("PRINCIPLE.缓存击穿")
                .newCoveredDomains(List.of(
                        EvaluationDecisionOutput.CoveredDomain.builder()
                                .domainId(6L)
                                .domainName("Redis")
                                .build()
                ))
                .newCoveredPoints(List.of("Redis / 缓存击穿基础方案"))
                .newCandidatePointsByDomain(List.of(
                        EvaluationDecisionOutput.CandidatePointsByDomain.builder()
                                .domainId(6L)
                                .domainName("Redis")
                                .points(List.of("热点 key", "缓存雪崩"))
                                .build()
                ))
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-1", 22L);

        assertThat(newLedger.get("covered_domains")).isEqualTo(List.of("Redis"));
        assertThat(newLedger.get("covered_points")).isEqualTo(List.of("Redis / 缓存击穿基础方案"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidatePoints = (List<Map<String, Object>>) newLedger.get("candidate_points_by_domain");
        assertThat(candidatePoints).hasSize(1);
        assertThat(candidatePoints.getFirst()).containsEntry("domainId", 6L);
        assertThat(candidatePoints.getFirst()).containsEntry("domainName", "Redis");
        assertThat(candidatePoints.getFirst().get("points")).isEqualTo(List.of("热点 key", "缓存雪崩"));

        @SuppressWarnings("unchecked")
        Map<String, Object> redisState = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(redisState.get("status")).isEqualTo("COVERED");
        assertThat(redisState.get("saturated")).isEqualTo(true);
        assertThat(redisState.get("evidenceRefs")).isEqualTo(List.of(22L));
    }

    @Test
    void reduce_shouldKeepCurrentDomainInProgressWhenNotCovered() {
        Map<String, Object> oldLedger = baseLedger();

        LedgerMutation mutation = LedgerMutation.builder()
                .questionType("PRINCIPLE")
                .currentDomainCode("redis")
                .currentDomainId(6L)
                .currentFocus("缓存击穿")
                .build();

        Map<String, Object> newLedger = reducer.reduce(oldLedger, mutation, "attempt-redis-2", 23L);

        @SuppressWarnings("unchecked")
        Map<String, Object> redisState = (Map<String, Object>) ((List<?>) newLedger.get("domain_states")).getFirst();
        assertThat(redisState.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(redisState.get("saturated")).isEqualTo(false);
        assertThat(redisState.get("evidenceRefs")).isEqualTo(List.of(23L));
        assertThat(newLedger.get("current_focus")).isEqualTo("缓存击穿");
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
        ledger.put("candidate_points_by_domain", List.of());
        ledger.put("recent_question_families", List.of());
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
