package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DecisionRepairOrchestrator {

    private final AiClient aiClient;
    private final DecisionExecutionPlanBuilder planBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RepairResult repair(EvaluationDecisionInput originalInput,
                               InterviewQuestion currentQuestion,
                               List<String> validationErrors,
                               String rawDecisionOutput) {
        EvaluationDecisionInput repairInput = buildRepairInput(originalInput, validationErrors, rawDecisionOutput);
        AiCallResult<EvaluationDecisionOutput> callResult = aiClient.callEvaluationDecision(repairInput);
        EvaluationDecisionOutput repaired = extractRawOutput(callResult);
        List<String> availableStrategyCodes = repairInput.getAvailableStrategies() == null
                ? List.of()
                : repairInput.getAvailableStrategies().stream()
                .map(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                .filter(code -> code != null && !code.isBlank())
                .toList();
        DecisionValidationResult validation = planBuilder.build(
                currentQuestion,
                repaired,
                repairInput.getRemainingTargetDomains(),
                availableStrategyCodes,
                DecisionExecutionPlan.EffectiveDecisionSource.REPAIRED_AI
        );
        return new RepairResult(validation.isValid(), repairInput, repaired, validation.getPlan(), validation.getErrorCodes());
    }

    private EvaluationDecisionOutput extractRawOutput(AiCallResult<EvaluationDecisionOutput> callResult) {
        if (callResult == null) {
            return null;
        }
        String rawResponse = callResult.getRawResponse();
        if (rawResponse != null && !rawResponse.isBlank()) {
            try {
                return objectMapper.readValue(rawResponse, EvaluationDecisionOutput.class);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return callResult.getOutput();
    }

    EvaluationDecisionInput buildRepairInput(EvaluationDecisionInput originalInput,
                                             List<String> validationErrors,
                                             String rawDecisionOutput) {
        return EvaluationDecisionInput.builder()
                .interviewId(originalInput.getInterviewId())
                .currentQuestionId(originalInput.getCurrentQuestionId())
                .interview(originalInput.getInterview())
                .questionIndex(originalInput.getQuestionIndex())
                .maxQuestions(originalInput.getMaxQuestions())
                .quotaSnapshot(originalInput.getQuotaSnapshot())
                .projectAndInternshipSummary(originalInput.getProjectAndInternshipSummary())
                .remainingTargetDomains(originalInput.getRemainingTargetDomains())
                .coveredKnowledgeSummary(List.of())
                .crossSessionBlockedKnowledgePoints(originalInput.getCrossSessionBlockedKnowledgePoints())
                .availableStrategies(originalInput.getAvailableStrategies())
                .currentQuestion(originalInput.getCurrentQuestion())
                .answerText(originalInput.getAnswerText())
                .expectedPoints(originalInput.getExpectedPoints())
                .retrievedMaterials(List.of())
                .recentInterviewMemory(originalInput.getRecentInterviewMemory())
                .repairMode(true)
                .repairAttemptNo(1)
                .rawDecisionOutput(rawDecisionOutput == null ? "" : rawDecisionOutput)
                .validationErrors(validationErrors == null ? List.of() : List.copyOf(validationErrors))
                .build();
    }

    public record RepairResult(boolean success,
                               EvaluationDecisionInput repairInput,
                               EvaluationDecisionOutput repairedOutput,
                               DecisionExecutionPlan plan,
                               List<String> errorCodes) {
    }
}
