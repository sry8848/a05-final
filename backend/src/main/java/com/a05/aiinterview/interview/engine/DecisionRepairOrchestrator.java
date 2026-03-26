package com.a05.aiinterview.interview.engine;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.entity.InterviewQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DecisionRepairOrchestrator {

    private static final Pattern STRATEGY_CODE_PATTERN = Pattern.compile("\\bS_[A-Z_]+\\b");

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
                .rawDecisionOutput(buildRepairDecisionSummary(rawDecisionOutput))
                .validationErrors(validationErrors == null ? List.of() : List.copyOf(validationErrors))
                .build();
    }

    private String buildRepairDecisionSummary(String rawDecisionOutput) {
        if (rawDecisionOutput == null || rawDecisionOutput.isBlank()) {
            return "";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(rawDecisionOutput, Map.class);
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(sanitizeDecisionSummary(parsed));
        } catch (Exception ignored) {
            return redactStrategyCodes(rawDecisionOutput);
        }
    }

    private Map<String, Object> sanitizeDecisionSummary(Map<String, Object> parsed) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("decisionReason", sanitizeValue(parsed.get("decisionReason")));
        summary.put("interviewAction", sanitizeValue(parsed.get("interviewAction")));
        summary.put("finalDecision", "[REDACTED_USE_AVAILABLE_STRATEGIES]");
        summary.put("nextFocus", sanitizeValue(parsed.get("nextFocus")));
        summary.put("nextItemType", sanitizeValue(parsed.get("nextItemType")));
        summary.put("nextItemName", sanitizeValue(parsed.get("nextItemName")));
        summary.put("nextProjectPoint", sanitizeValue(parsed.get("nextProjectPoint")));
        summary.put("targetDomainCode", sanitizeValue(parsed.get("targetDomainCode")));
        summary.put("newCoveredDomains", sanitizeValue(parsed.get("newCoveredDomains")));
        summary.put("newCoveredPoints", sanitizeValue(parsed.get("newCoveredPoints")));
        summary.put("retrievalPlans", sanitizeValue(parsed.get("retrievalPlans")));
        return summary;
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return redactStrategyCodes(text);
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>(list.size());
            for (Object item : list) {
                sanitized.add(sanitizeValue(item));
            }
            return sanitized;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sanitized.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue()));
            }
            return sanitized;
        }
        return value;
    }

    private String redactStrategyCodes(String text) {
        return STRATEGY_CODE_PATTERN.matcher(text).replaceAll("[REDACTED_STRATEGY]");
    }

    public record RepairResult(boolean success,
                               EvaluationDecisionInput repairInput,
                               EvaluationDecisionOutput repairedOutput,
                               DecisionExecutionPlan plan,
                               List<String> errorCodes) {
    }
}
