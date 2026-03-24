package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SystemFallbackPlanBuilder tests")
class SystemFallbackPlanBuilderTest {

    private final SystemFallbackPlanBuilder builder = new SystemFallbackPlanBuilder();

    @Test
    @DisplayName("fallback should use session hash offset instead of always starting with decision")
    void fallback_shouldUseSessionHashOffset() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");

        DecisionExecutionPlan plan = builder.buildContinuePlan(1L, currentQuestion, 0);

        assertThat(plan.getEffectiveDecisionSource()).isEqualTo(DecisionExecutionPlan.EffectiveDecisionSource.SYSTEM_FALLBACK);
        assertThat(plan.getStrategyCode()).isEqualTo(StrategyCode.S_ENTER_BEHAVIORAL.code());
        assertThat(plan.getTargetQuestionType()).isEqualTo("BEHAVIORAL");
        assertThat(plan.getFallbackRotationIndex()).isZero();
        assertThat(plan.getFallbackDimension()).isEqualTo(SystemFallbackPlanBuilder.FallbackDimension.REFLECTION.name());
        assertThat(plan.getNextFocus()).contains("复盘");
    }

    @Test
    @DisplayName("fallback on behavioral question should rotate to S_B_NEW strategy")
    void fallbackOnBehavioralQuestion_shouldUseBehavioralNewStrategy() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("BEHAVIORAL");

        DecisionExecutionPlan plan = builder.buildContinuePlan(2L, currentQuestion, 2);

        assertThat(plan.getStrategyCode()).isEqualTo(StrategyCode.S_B_NEW_DECISION.code());
        assertThat(plan.getTargetQuestionType()).isEqualTo("BEHAVIORAL");
        assertThat(plan.getFallbackDimension()).isEqualTo(SystemFallbackPlanBuilder.FallbackDimension.DECISION.name());
    }
}
