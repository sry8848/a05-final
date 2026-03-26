package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.contract.StrategyCode;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DecisionRepairOrchestrator tests")
class DecisionRepairOrchestratorTest {

    @Test
    @DisplayName("repair should trim nonessential context before recalling evaluation_decision")
    void repair_shouldTrimNonessentialContext() {
        AiClient aiClient = mock(AiClient.class);
        DecisionExecutionPlanBuilder planBuilder = new DecisionExecutionPlanBuilder();
        DecisionRepairOrchestrator orchestrator = new DecisionRepairOrchestrator(aiClient, planBuilder);

        InterviewQuestion currentQuestion = new InterviewQuestion();
        currentQuestion.setQuestionType("PRINCIPLE");
        currentQuestion.setGenerationContextJson(java.util.Map.of(
                "domainCode", "DOMAIN_REDIS",
                "domainName", "Redis 缓存"
        ));

        EvaluationDecisionInput input = EvaluationDecisionInput.builder()
                .interviewId(10L)
                .currentQuestionId(11L)
                .currentQuestion(EvaluationDecisionInput.CurrentQuestionContext.builder()
                        .questionType("PRINCIPLE")
                        .domainCode("DOMAIN_REDIS")
                        .domainName("Redis 缓存")
                        .build())
                .answerText("回答")
                .expectedPoints(List.of("定义"))
                .projectAndInternshipSummary(List.of(EvaluationDecisionInput.ProjectAndInternshipItem.builder().itemName("P").build()))
                .availableStrategies(List.of(EvaluationDecisionInput.AvailableStrategy.builder()
                        .strategyCode(StrategyCode.S_P_VERIFY.code())
                        .build()))
                .remainingTargetDomains(List.of())
                .coveredKnowledgeSummary(List.of("已覆盖知识"))
                .retrievedMaterials(List.of(EvaluationDecisionInput.RetrievedMaterial.builder()
                        .query("redis")
                        .materials(List.of("long rag"))
                        .build()))
                .recentInterviewMemory(List.of())
                .build();

        EvaluationDecisionOutput repaired = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .finalDecision(StrategyCode.S_P_VERIFY.code())
                .nextFocus("缓存一致性")
                .targetDomainCode("")
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();
        when(aiClient.callEvaluationDecision(any())).thenReturn(
                AiCallResult.<EvaluationDecisionOutput>builder()
                        .output(repaired)
                        .rawResponse(null)
                        .build()
        );

        DecisionRepairOrchestrator.RepairResult result = orchestrator.repair(
                input,
                currentQuestion,
                List.of("STRATEGY_NOT_IN_AVAILABLE_POOL"),
                """
                        {
                          "decisionReason": "candidate prefers S_ENTER_PROJECT",
                          "finalDecision": "S_ENTER_PROJECT",
                          "nextFocus": "缓存一致性"
                        }
                        """
        );

        assertThat(result.success()).isTrue();
        assertThat(result.plan()).isNotNull();

        ArgumentCaptor<EvaluationDecisionInput> captor = ArgumentCaptor.forClass(EvaluationDecisionInput.class);
        verify(aiClient).callEvaluationDecision(captor.capture());
        EvaluationDecisionInput repairInput = captor.getValue();
        assertThat(repairInput.getRetrievedMaterials()).isEmpty();
        assertThat(repairInput.getCoveredKnowledgeSummary()).isEmpty();
        assertThat(repairInput.getRepairMode()).isTrue();
        assertThat(repairInput.getRepairAttemptNo()).isEqualTo(1);
        assertThat(repairInput.getValidationErrors()).containsExactly("STRATEGY_NOT_IN_AVAILABLE_POOL");
        assertThat(repairInput.getRawDecisionOutput()).contains("缓存一致性");
        assertThat(repairInput.getRawDecisionOutput()).doesNotContain("S_ENTER_PROJECT");
    }
}
