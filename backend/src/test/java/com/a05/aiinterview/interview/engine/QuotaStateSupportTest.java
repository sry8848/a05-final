package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuotaStateSupport tests")
class QuotaStateSupportTest {

    @Test
    @DisplayName("advance should update principle counters for guide-and-verify")
    void advance_shouldUpdatePrincipleCountersForGuideAndVerify() {
        Map<String, Object> reduced = QuotaStateSupport.advance(
                Map.of(
                        "samePointContinue", 1,
                        "sameDomainContinue", 2,
                        "sameProjectPointContinue", 4,
                        "sameProjectContinue", 5,
                        "principleTotal", 3,
                        "projectTotal", 2,
                        "scenarioTotal", 1,
                        "behavioralTotal", 0
                ),
                "PRINCIPLE",
                StrategyCode.S_P_VERIFY,
                "PRINCIPLE"
        );

        assertThat(reduced).containsEntry("samePointContinue", 2)
                .containsEntry("sameDomainContinue", 3)
                .containsEntry("sameProjectPointContinue", 0)
                .containsEntry("sameProjectContinue", 0)
                .containsEntry("principleTotal", 4);
    }

    @Test
    @DisplayName("advance should clear principle counters when exiting current type")
    void advance_shouldClearPrincipleCountersWhenExitingCurrentType() {
        Map<String, Object> reduced = QuotaStateSupport.advance(
                QuotaStateSupport.initialQuotaState(),
                "PRINCIPLE",
                StrategyCode.S_ENTER_PROJECT,
                "PROJECT_DEEP_DIVE"
        );

        assertThat(reduced).containsEntry("samePointContinue", 0)
                .containsEntry("sameDomainContinue", 0)
                .containsEntry("projectTotal", 1);
    }

    @Test
    @DisplayName("advance should keep project chain when switching project point")
    void advance_shouldKeepProjectChainWhenSwitchingProjectPoint() {
        Map<String, Object> reduced = QuotaStateSupport.advance(
                Map.of(
                        "samePointContinue", 0,
                        "sameDomainContinue", 0,
                        "sameProjectPointContinue", 2,
                        "sameProjectContinue", 3,
                        "principleTotal", 1,
                        "projectTotal", 4,
                        "scenarioTotal", 0,
                        "behavioralTotal", 0
                ),
                "PROJECT_DEEP_DIVE",
                StrategyCode.S_J_SWITCH_POINT,
                "PROJECT_DEEP_DIVE"
        );

        assertThat(reduced).containsEntry("sameProjectPointContinue", 0)
                .containsEntry("sameProjectContinue", 4)
                .containsEntry("projectTotal", 5);
    }

    @Test
    @DisplayName("ensureQuotaState should backfill totals from existing question types")
    void ensureQuotaState_shouldBackfillTotalsFromExistingQuestionTypes() {
        InterviewQuestion q1 = new InterviewQuestion();
        q1.setQuestionType("PRINCIPLE");
        InterviewQuestion q2 = new InterviewQuestion();
        q2.setQuestionType("PROJECT_DEEP_DIVE");
        InterviewQuestion q3 = new InterviewQuestion();
        q3.setQuestionType("PROJECT_DEEP_DIVE");
        InterviewQuestion q4 = new InterviewQuestion();
        q4.setQuestionType("BEHAVIORAL");

        Map<String, Object> ledger = new LinkedHashMap<>();

        Map<String, Object> quotaState = QuotaStateSupport.ensureQuotaState(ledger, List.of(q1, q2, q3, q4));

        assertThat(quotaState).containsEntry("samePointContinue", 0)
                .containsEntry("sameDomainContinue", 0)
                .containsEntry("sameProjectPointContinue", 0)
                .containsEntry("sameProjectContinue", 0)
                .containsEntry("principleTotal", 1)
                .containsEntry("projectTotal", 2)
                .containsEntry("scenarioTotal", 0)
                .containsEntry("behavioralTotal", 1);
    }
}
