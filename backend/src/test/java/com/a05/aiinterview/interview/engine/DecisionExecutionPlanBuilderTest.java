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
                "domainCode", "redis",
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
        assertThat(result.getPlan().getTargetDomainCode()).isEqualTo("redis");
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
                        .domainCode("redis")
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

    @Test
    @DisplayName("project fields should be preserved on valid CONTINUE output")
    void projectFields_shouldBePreservedOnValidContinueOutput() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("INTRO");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_ENTER_PROJECT.code())
                .nextFocus("延迟消息与并发控制")
                .nextItemType("PROJECT")
                .nextItemName("Chabst")
                .nextProjectPoint("RabbitMQ 延迟消息处理超时订单")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(),
                List.of(StrategyCode.S_ENTER_PROJECT.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPlan().getNextItemType()).isEqualTo("PROJECT");
        assertThat(result.getPlan().getNextItemName()).isEqualTo("Chabst");
        assertThat(result.getPlan().getNextProjectPoint()).isEqualTo("RabbitMQ 延迟消息处理超时订单");
    }

    @Test
    @DisplayName("multiple retrieval plans should fail validation")
    void multipleRetrievalPlans_shouldFailValidation() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("INTRO");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_ENTER_PROJECT.code())
                .nextFocus("Seata 事务边界")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of(
                        retrievalPlan("Seata 事务边界", List.of("Seata"), "L3"),
                        retrievalPlan("Feign Header 透传", List.of("Feign"), "L3")
                ))
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(),
                List.of(StrategyCode.S_ENTER_PROJECT.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCodes()).contains("RETRIEVAL_PLAN_COUNT_INVALID");
        assertThat(result.getPlan()).isNull();
    }

    @Test
    @DisplayName("retrieval plan without query text should fail validation")
    void retrievalPlanWithoutQueryText_shouldFailValidation() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("INTRO");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_ENTER_PROJECT.code())
                .nextFocus("Seata 事务边界")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of(retrievalPlan("", List.of("Seata"), "L3")))
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(),
                List.of(StrategyCode.S_ENTER_PROJECT.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCodes()).contains("RETRIEVAL_QUERY_TEXT_REQUIRED");
        assertThat(result.getPlan()).isNull();
    }

    @Test
    @DisplayName("retrieval plan with invalid difficulty hint should fail validation")
    void retrievalPlanWithInvalidDifficultyHint_shouldFailValidation() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("INTRO");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_ENTER_PROJECT.code())
                .nextFocus("Seata 事务边界")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of(retrievalPlan("Seata 事务边界", List.of("Seata"), "L6")))
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(),
                List.of(StrategyCode.S_ENTER_PROJECT.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorCodes()).contains("RETRIEVAL_DIFFICULTY_HINT_INVALID");
        assertThat(result.getPlan()).isNull();
    }

    @Test
    @DisplayName("retrieval plan with empty keyword hints should pass validation")
    void retrievalPlanWithEmptyKeywordHints_shouldPassValidation() {
        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("INTRO");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_ENTER_PROJECT.code())
                .nextFocus("Seata 事务边界")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of(retrievalPlan("Seata 事务边界", List.of(), "L3")))
                .build();

        DecisionValidationResult result = builder.build(
                currentQuestion,
                output,
                List.of(),
                List.of(StrategyCode.S_ENTER_PROJECT.code()),
                DecisionExecutionPlan.EffectiveDecisionSource.RAW_AI
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.getPlan()).isNotNull();
        assertThat(result.getPlan().getRetrievalPlans()).hasSize(1);
        assertThat(result.getPlan().getRetrievalPlans().getFirst().getKeywordHints()).isEmpty();
    }

    private EvaluationDecisionOutput.RetrievalPlan retrievalPlan(
            String queryText,
            List<String> keywordHints,
            String difficultyHint
    ) {
        return EvaluationDecisionOutput.RetrievalPlan.builder()
                .queryText(queryText)
                .keywordHints(keywordHints)
                .difficultyHint(difficultyHint)
                .build();
    }
}
