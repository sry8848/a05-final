package com.a05.aiinterview.interview.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewerArchetypeSupport tests")
class InterviewerArchetypeSupportTest {

    @Test
    @DisplayName("chooseForSession should be deterministic for same session id")
    void chooseForSession_shouldBeDeterministic() {
        String first = InterviewerArchetypeSupport.chooseForSession(42L);
        String second = InterviewerArchetypeSupport.chooseForSession(42L);

        assertThat(first).isEqualTo(second);
        assertThat(first).isIn("efficiency", "guiding", "stress");
    }

    @Test
    @DisplayName("chooseForSession should distribute across all archetypes")
    void chooseForSession_shouldDistributeAcrossArchetypes() {
        assertThat(Map.of(
                InterviewerArchetypeSupport.chooseForSession(1L), true,
                InterviewerArchetypeSupport.chooseForSession(2L), true,
                InterviewerArchetypeSupport.chooseForSession(3L), true
        ).keySet()).contains("efficiency", "guiding", "stress");
    }

    @Test
    @DisplayName("resolveFromLedger should fallback to efficiency when missing")
    void resolveFromLedger_shouldFallbackToEfficiencyWhenMissing() {
        assertThat(InterviewerArchetypeSupport.resolveFromLedger(null)).isEqualTo("efficiency");
        assertThat(InterviewerArchetypeSupport.resolveFromLedger(Map.of())).isEqualTo("efficiency");
    }

    @Test
    @DisplayName("resolveFromLedger should keep valid persisted archetype")
    void resolveFromLedger_shouldKeepValidPersistedArchetype() {
        Map<String, Object> ledger = new LinkedHashMap<>();
        ledger.put("interviewer_archetype", "stress");

        assertThat(InterviewerArchetypeSupport.resolveFromLedger(ledger)).isEqualTo("stress");
    }
}
