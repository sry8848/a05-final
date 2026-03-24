package com.a05.aiinterview.interview.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DecisionFallbackStateSupport tests")
class DecisionFallbackStateSupportTest {

    @Test
    @DisplayName("system fallback plan should increment hidden counters")
    void systemFallbackPlan_shouldIncrementHiddenCounters() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("decision_fallback_state", Map.of(
                "rotationIndex", 1,
                "consecutiveFallbackCount", 0,
                "totalFallbackCount", 2
        ));

        DecisionExecutionPlan plan = DecisionExecutionPlan.builder()
                .interviewAction("CONTINUE")
                .effectiveDecisionSource(DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK)
                .build();

        DecisionFallbackStateSupport.applyPlan(ledger, plan);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) ledger.get("decision_fallback_state");
        assertThat(state).containsEntry("rotationIndex", 2)
                .containsEntry("consecutiveFallbackCount", 1)
                .containsEntry("totalFallbackCount", 3);
    }

    @Test
    @DisplayName("normal continue should reset consecutive fallback count")
    void normalContinue_shouldResetConsecutiveFallbackCount() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("decision_fallback_state", Map.of(
                "rotationIndex", 2,
                "consecutiveFallbackCount", 1,
                "totalFallbackCount", 2
        ));

        DecisionExecutionPlan plan = DecisionExecutionPlan.builder()
                .interviewAction("CONTINUE")
                .effectiveDecisionSource(DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI)
                .build();

        DecisionFallbackStateSupport.applyPlan(ledger, plan);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) ledger.get("decision_fallback_state");
        assertThat(state).containsEntry("rotationIndex", 2)
                .containsEntry("consecutiveFallbackCount", 0)
                .containsEntry("totalFallbackCount", 2);
    }
}
