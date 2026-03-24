package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AvailableStrategyAssembler tests")
class AvailableStrategyAssemblerTest {

    private final AvailableStrategyAssembler assembler = new AvailableStrategyAssembler();

    @Test
    @DisplayName("principle question should include principle internals, then enter actions except enter principle")
    void principleQuestionShouldIncludePrincipleInternalsThenEnterActionsExceptEnterPrinciple() {
        List<EvaluationDecisionInput.AvailableStrategy> strategies = assembler.assemble(
                "PRINCIPLE",
                true,
                remainingDomains("DOMAIN_REDIS"),
                quotaState()
        );

        assertThat(strategies)
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .containsSubsequence(
                        StrategyCode.S_P_VERIFY.code(),
                        StrategyCode.S_P_DEEP_LINK.code(),
                        StrategyCode.S_P_VARIANT.code(),
                        StrategyCode.S_P_SAME_DOMAIN_SHIFT.code(),
                        StrategyCode.S_SWITCH_DOMAIN.code()
                )
                .contains(
                        StrategyCode.S_ENTER_PROJECT.code(),
                        StrategyCode.S_ENTER_SCENARIO.code(),
                        StrategyCode.S_ENTER_BEHAVIORAL.code(),
                        StrategyCode.S_WRAPUP.code()
                )
                .doesNotContain(StrategyCode.S_ENTER_PRINCIPLE.code());
    }

    @Test
    @DisplayName("scenario question should include follow and new scenario strategies, then non-scenario enter actions")
    void scenarioQuestionShouldIncludeFollowAndNewStrategiesThenNonScenarioEnterActions() {
        List<EvaluationDecisionInput.AvailableStrategy> strategies = assembler.assemble(
                "SCENARIO",
                true,
                remainingDomains("DOMAIN_MYSQL"),
                quotaState()
        );

        assertThat(strategies)
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .containsSubsequence(
                        StrategyCode.S_S_FOLLOW_DIAGNOSE.code(),
                        StrategyCode.S_S_FOLLOW_RESPONSE.code(),
                        StrategyCode.S_S_FOLLOW_TRADEOFF.code(),
                        StrategyCode.S_S_FOLLOW_GUARDRAILS.code(),
                        StrategyCode.S_S_NEW_DIAGNOSE.code(),
                        StrategyCode.S_S_NEW_RESPONSE.code(),
                        StrategyCode.S_S_NEW_TRADEOFF.code(),
                        StrategyCode.S_S_NEW_GUARDRAILS.code()
                )
                .contains(
                        StrategyCode.S_ENTER_PROJECT.code(),
                        StrategyCode.S_ENTER_PRINCIPLE.code(),
                        StrategyCode.S_ENTER_BEHAVIORAL.code(),
                        StrategyCode.S_WRAPUP.code()
                )
                .doesNotContain(StrategyCode.S_ENTER_SCENARIO.code());
    }

    @Test
    @DisplayName("behavioral question should include follow and new behavioral strategies, then non-behavioral enter actions")
    void behavioralQuestionShouldIncludeFollowAndNewStrategiesThenNonBehavioralEnterActions() {
        List<EvaluationDecisionInput.AvailableStrategy> strategies = assembler.assemble(
                "BEHAVIORAL",
                true,
                remainingDomains("DOMAIN_JVM"),
                quotaState()
        );

        assertThat(strategies)
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .containsSubsequence(
                        StrategyCode.S_B_FOLLOW_DECISION.code(),
                        StrategyCode.S_B_FOLLOW_REFLECTION.code(),
                        StrategyCode.S_B_FOLLOW_CONFLICT.code(),
                        StrategyCode.S_B_FOLLOW_TRANSFER.code(),
                        StrategyCode.S_B_NEW_DECISION.code(),
                        StrategyCode.S_B_NEW_REFLECTION.code(),
                        StrategyCode.S_B_NEW_CONFLICT.code(),
                        StrategyCode.S_B_NEW_TRANSFER.code()
                )
                .contains(
                        StrategyCode.S_ENTER_PROJECT.code(),
                        StrategyCode.S_ENTER_PRINCIPLE.code(),
                        StrategyCode.S_ENTER_SCENARIO.code(),
                        StrategyCode.S_WRAPUP.code()
                )
                .doesNotContain(StrategyCode.S_ENTER_BEHAVIORAL.code());
    }

    @Test
    @DisplayName("should delete blocked strategies when limits are full")
    void shouldDeleteBlockedStrategiesWhenLimitsAreFull() {
        List<EvaluationDecisionInput.AvailableStrategy> strategies = assembler.assemble(
                "PRINCIPLE",
                true,
                remainingDomains("DOMAIN_REDIS"),
                quotaState(
                        QuotaStateSupport.SAME_POINT_CONTINUE, 20,
                        QuotaStateSupport.SAME_DOMAIN_CONTINUE, 20,
                        QuotaStateSupport.PRINCIPLE_TOTAL, 20,
                        QuotaStateSupport.PROJECT_TOTAL, 20
                )
        );

        assertThat(strategies)
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .doesNotContain(
                        StrategyCode.S_P_VERIFY.code(),
                        StrategyCode.S_P_VARIANT.code(),
                        StrategyCode.S_P_DEEP_LINK.code(),
                        StrategyCode.S_P_SAME_DOMAIN_SHIFT.code(),
                        StrategyCode.S_SWITCH_DOMAIN.code(),
                        StrategyCode.S_ENTER_PRINCIPLE.code(),
                        StrategyCode.S_ENTER_PROJECT.code()
                )
                .contains(
                        StrategyCode.S_ENTER_SCENARIO.code(),
                        StrategyCode.S_ENTER_BEHAVIORAL.code(),
                        StrategyCode.S_WRAPUP.code()
                );
    }

    @Test
    @DisplayName("should delete enter-project when project context is missing")
    void shouldDeleteEnterProjectWhenProjectContextIsMissing() {
        List<EvaluationDecisionInput.AvailableStrategy> strategies = assembler.assemble(
                "PRINCIPLE",
                false,
                remainingDomains("DOMAIN_REDIS"),
                quotaState()
        );

        assertThat(strategies)
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .doesNotContain(StrategyCode.S_ENTER_PROJECT.code());
    }

    @Test
    @DisplayName("should delete target-domain strategies when remaining domains menu is empty")
    void shouldDeleteTargetDomainStrategiesWhenRemainingDomainsMenuIsEmpty() {
        List<EvaluationDecisionInput.AvailableStrategy> strategies = assembler.assemble(
                "PRINCIPLE",
                true,
                List.of(),
                quotaState()
        );

        assertThat(strategies)
                .extracting(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .doesNotContain(
                        StrategyCode.S_SWITCH_DOMAIN.code(),
                        StrategyCode.S_ENTER_PRINCIPLE.code()
                );
    }

    private static List<EvaluationDecisionInput.RemainingTargetDomain> remainingDomains(String domainCode) {
        return List.of(EvaluationDecisionInput.RemainingTargetDomain.builder()
                .domainCode(domainCode)
                .domainName(domainCode)
                .focusPoints(List.of("point"))
                .build());
    }

    private static Map<String, Object> quotaState(Object... pairs) {
        Map<String, Object> quotaState = QuotaStateSupport.initialQuotaState();
        for (int i = 0; i < pairs.length; i += 2) {
            quotaState.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return quotaState;
    }
}
