package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DecisionExecutionPlanBuilder tests")
class DecisionExecutionPlanBuilderTest {

    private final DecisionExecutionPlanBuilder builder = new DecisionExecutionPlanBuilder();

    @Test
    @DisplayName("same-domain principle strategy should inherit current question domain")
    void sameDomainPrincipleStrategy_shouldInheritCurrentQuestionDomain() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setGenerationContextJson(Map.of(
                "domainCode", "DOMAIN_REDIS",
                "domainName", "Redis 缓存"
        ));

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_P_VERIFY.code())
                .nextFocus("缓存一致性")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(),
                List.of(StrategyCode.S_P_VERIFY.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPlan()).isNotNull();
        assertThat(result.getPlan().getTargetDomainCode()).isEqualTo("DOMAIN_REDIS");
        assertThat(result.getPlan().getTargetDomainName()).isEqualTo("Redis 缓存");
        assertThat(result.getPlan().getTargetQuestionType()).isEqualTo("PRINCIPLE");
    }

    @Test
    @DisplayName("enter principle without target domain should fail validation")
    void enterPrincipleWithoutTargetDomain_shouldFailValidation() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PROJECT_DEEP_DIVE");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_ENTER_PRINCIPLE.code())
                .nextFocus("Redis 持久化")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(EvaluationDecisionInput.RemainingTargetDomain.builder()
                        .domainCode("DOMAIN_REDIS")
                        .domainName("Redis 缓存")
                        .focusPoints(List.of("持久化"))
                        .build()),
                List.of(StrategyCode.S_ENTER_PRINCIPLE.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCodes()).contains("TARGET_DOMAIN_REQUIRED");
        assertThat(result.getPlan()).isNull();
    }
}
