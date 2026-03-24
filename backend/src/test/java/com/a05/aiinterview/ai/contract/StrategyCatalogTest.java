package com.a05.aiinterview.ai.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StrategyCatalog tests")
class StrategyCatalogTest {

    @Test
    @DisplayName("should expose all scenario and behavioral follow/new strategies")
    void shouldExposeScenarioAndBehavioralFollowAndNewStrategies() {
        Set<String> codes = StrategyCatalog.all().stream()
                .map(definition -> definition.code().code())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(codes).contains(
                StrategyCode.S_S_FOLLOW_DIAGNOSE.code(),
                StrategyCode.S_S_FOLLOW_RESPONSE.code(),
                StrategyCode.S_S_FOLLOW_TRADEOFF.code(),
                StrategyCode.S_S_FOLLOW_GUARDRAILS.code(),
                StrategyCode.S_S_NEW_DIAGNOSE.code(),
                StrategyCode.S_S_NEW_RESPONSE.code(),
                StrategyCode.S_S_NEW_TRADEOFF.code(),
                StrategyCode.S_S_NEW_GUARDRAILS.code(),
                StrategyCode.S_B_FOLLOW_DECISION.code(),
                StrategyCode.S_B_FOLLOW_REFLECTION.code(),
                StrategyCode.S_B_FOLLOW_CONFLICT.code(),
                StrategyCode.S_B_FOLLOW_TRANSFER.code(),
                StrategyCode.S_B_NEW_DECISION.code(),
                StrategyCode.S_B_NEW_REFLECTION.code(),
                StrategyCode.S_B_NEW_CONFLICT.code(),
                StrategyCode.S_B_NEW_TRANSFER.code()
        );
    }

    @Test
    @DisplayName("should keep wrapup as the only wrapup strategy")
    void shouldKeepWrapupAsTheOnlyWrapupStrategy() {
        assertThat(StrategyCatalog.isWrapup(StrategyCode.S_WRAPUP.code())).isTrue();
        assertThat(StrategyCatalog.isWrapup(StrategyCode.S_ENTER_PROJECT.code())).isFalse();
    }

    @Test
    @DisplayName("should require target domain only for switch-domain and enter-principle")
    void shouldRequireTargetDomainOnlyForSwitchDomainAndEnterPrinciple() {
        assertThat(StrategyCatalog.requiresTargetDomain(StrategyCode.S_SWITCH_DOMAIN.code())).isTrue();
        assertThat(StrategyCatalog.requiresTargetDomain(StrategyCode.S_ENTER_PRINCIPLE.code())).isTrue();
        assertThat(StrategyCatalog.requiresTargetDomain(StrategyCode.S_P_VERIFY.code())).isFalse();
        assertThat(StrategyCatalog.requiresTargetDomain(StrategyCode.S_J_PRESSURE.code())).isFalse();
    }

    @Test
    @DisplayName("should encode blocking limits and updates into strategy definitions")
    void shouldEncodeBlockingLimitsAndUpdatesIntoStrategyDefinitions() {
        StrategyDefinition principleVerify = StrategyCatalog.find(StrategyCode.S_P_VERIFY.code()).orElseThrow();
        assertThat(principleVerify.blockingLimits()).containsExactlyInAnyOrder(
                StrategyLimit.SAME_POINT_CONTINUE,
                StrategyLimit.SAME_DOMAIN_CONTINUE,
                StrategyLimit.PRINCIPLE_TOTAL
        );
        assertThat(principleVerify.quotaUpdatePolicy().increments()).containsExactlyInAnyOrder(
                StrategyLimit.SAME_POINT_CONTINUE,
                StrategyLimit.SAME_DOMAIN_CONTINUE,
                StrategyLimit.PRINCIPLE_TOTAL
        );

        StrategyDefinition scenarioNew = StrategyCatalog.find(StrategyCode.S_S_NEW_DIAGNOSE.code()).orElseThrow();
        assertThat(scenarioNew.blockingLimits()).containsExactly(StrategyLimit.SCENARIO_TOTAL);
        assertThat(scenarioNew.quotaUpdatePolicy().increments()).containsExactly(StrategyLimit.SCENARIO_TOTAL);
        assertThat(scenarioNew.quotaUpdatePolicy().resets()).isEmpty();
    }
}
