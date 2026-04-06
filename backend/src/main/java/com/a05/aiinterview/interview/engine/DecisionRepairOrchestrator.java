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

/**
     * 决策修复编排器，容错机制的第二级：在 AI 决策验证失败时尝试修复。
     *
     * <p>核心设计思想：
     * <ul>
     *   <li>不是直接重试，而是构建一个特殊的"修复模式"输入</li>
     *   <li>在输入中包含原始失败输出和验证错误，让 AI 知道问题在哪里</li>
     *   <li>同时对原始输出进行脱敏处理（隐藏策略编码），避免 AI 重复之前的错误</li>
     *   <li>修复后再次进行验证，确保修复结果有效</li>
     * </ul>
     *
     * <p>修复流程：
     * <ol>
     *   <li>构建修复输入（repairMode=true + 原始失败输出 + 验证错误）</li>
     *   <li>调用 AI 进行修复决策</li>
     *   <li>再次验证修复后的决策</li>
     *   <li>返回修复结果（成功/失败 + 修复后的计划）</li>
     * </ol>
     */
    @Component
    @RequiredArgsConstructor
    public class DecisionRepairOrchestrator {

        /**
         * 策略编码正则匹配模式。
         *
         * <p>匹配格式：\bS_[A-Z_]+\b
         * <ul>
         *   <li>\b：单词边界</li>
         *   <li>S_：固定前缀</li>
         *   <li>[A-Z_]+：一个大写字母或下划线组成的序列</li>
         * </ul>
         *
         * <p>匹配示例：S_PRINCIPLE、S_PROJECT_DIG、S_B_NEW_DECISION 等
         */
        private static final Pattern STRATEGY_CODE_PATTERN = Pattern.compile("\\bS_[A-Z_]+\\b");

        private final AiClient aiClient;
        private final DecisionExecutionPlanBuilder planBuilder;
        private final ObjectMapper objectMapper = new ObjectMapper();

        /**
         * 执行决策修复的主入口方法。
         *
         * <p>处理流程：
         * <ol>
         *   <li>构建修复输入（包含 repairMode=true、原始失败输出、脱敏后的验证错误）</li>
         *   <li>调用 AI evaluation-decision 模型进行修复决策</li>
         *   <li>提取修复后的输出</li>
         *   <li>再次验证修复后的决策是否合法</li>
         *   <li>返回修复结果（成功/失败、修复输入、修复后输出、有效计划、错误列表）</li>
         * </ol>
         *
         * @param originalInput 原始评估决策输入
         * @param currentQuestion 当前题目
         * @param validationErrors 验证错误列表（来自第一级验证）
         * @param rawDecisionOutput 原始决策输出（未脱敏的 AI 响应）
         * @return 修复结果，包含是否成功、修复输入、修复后输出、有效计划、错误列表
         */
        public RepairResult repair(EvaluationDecisionInput originalInput,
                                   InterviewQuestion currentQuestion,
                                   List<String> validationErrors,
                                   String rawDecisionOutput) {
            // 步骤1：构建修复输入
            EvaluationDecisionInput repairInput = buildRepairInput(originalInput, validationErrors, rawDecisionOutput);

            // 步骤2：调用 AI 进行修复决策（repairMode=true）
            AiCallResult<EvaluationDecisionOutput> callResult = aiClient.callEvaluationDecision(repairInput);

            // 步骤3：提取修复后的输出
            EvaluationDecisionOutput repaired = extractRawOutput(callResult);

            // 步骤4：提取可用策略编码列表（用于验证）
            List<String> availableStrategyCodes = repairInput.getAvailableStrategies() == null
                    ? List.of()
                    : repairInput.getAvailableStrategies().stream()
                    .map(EvaluationDecisionInput.AvailableStrategy::getStrategyCode)
                    .filter(code -> code != null && !code.isBlank())
                    .toList();

            // 步骤5：再次验证修复后的决策
            DecisionValidationResult validation = planBuilder.build(
                    currentQuestion,
                    repaired,
                    repairInput.getRemainingTargetDomains(),
                    availableStrategyCodes,
                    DecisionExecutionPlan.EffectiveDecisionSource.REPAIRED_AI
            );

            // 步骤6：返回修复结果
            return new RepairResult(
                    validation.isValid(),     // 是否修复成功
                    repairInput,              // 修复输入
                    repaired,                 // 修复后输出
                    validation.getPlan(),     // 修复后的计划
                    validation.getErrorCodes() // 剩余错误列表
            );
        }

        /**
         * 从 AI 调用结果中提取原始输出。
         *
         * <p>优先从 rawResponse 解析（因为这是模型原始输出），
         * 如果解析失败则降级到 callResult.getOutput()。
         *
         * @param callResult AI 调用结果
         * @return 原始输出对象
         */
        private EvaluationDecisionOutput extractRawOutput(AiCallResult<EvaluationDecisionOutput> callResult) {
            if (callResult == null) {
                return null;
            }
            String rawResponse = callResult.getRawResponse();
            if (rawResponse != null && !rawResponse.isBlank()) {
                try {
                    // 尝试从原始响应中解析 JSON
                    return objectMapper.readValue(rawResponse, EvaluationDecisionOutput.class);
                } catch (Exception ignored) {
                    // JSON 解析失败，降级到 getOutput()
                }
            }
            return callResult.getOutput();
        }

        /**
         * 构建修复模式的评估决策输入。
         *
         * <p>与原始输入的区别：
         * <ul>
         *   <li>repairMode 设置为 true，告知 AI 这是修复场景</li>
         *   <li>repairAttemptNo 设置为 1，表示第几次修复尝试</li>
         *   <li>rawDecisionOutput 设置为脱敏后的原始失败输出</li>
         *   <li>validationErrors 设置为验证错误列表</li>
         * </ul>
         *
         * <p>脱敏处理说明：
         * <ul>
         *   <li>finalDecision 字段会被替换为 [REDACTED_USE_AVAILABLE_STRATEGIES]</li>
         *   <li>所有策略编码（S_XXX）会被替换为 [REDACTED_STRATEGY]</li>
         *   <li>这样可以避免 AI 重复之前的错误策略</li>
         * </ul>
         *
         * @param originalInput 原始评估决策输入
         * @param validationErrors 验证错误列表
         * @param rawDecisionOutput 原始决策输出（未脱敏）
         * @return 修复模式的评估决策输入
         */
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

    /**
         * 构建修复决策摘要（脱敏处理）。
         *
         * <p>处理流程：
         * <ol>
         *   <li>尝试解析 JSON</li>
         *   <li>如果成功，对摘要进行脱敏处理</li>
         *   <li>如果失败，直接对原始文本进行策略编码脱敏</li>
         * </ol>
         *
         * @param rawDecisionOutput 原始决策输出
         * @return 脱敏后的决策摘要
         */
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

        /**
         * 对决策摘要进行脱敏处理。
         *
         * <p>脱敏策略：
         * <ul>
         *   <li>finalDecision → "[REDACTED_USE_AVAILABLE_STRATEGIES]"（强制使用可用策略列表）</li>
         *   <li>其他字段：递归调用 sanitizeValue 进行深度脱敏</li>
         * </ul>
         *
         * @param parsed 解析后的决策 Map
         * @return 脱敏后的摘要 Map
         */
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

        /**
         * 递归脱敏处理，深度遍历所有嵌套结构。
         *
         * <p>处理逻辑：
         * <ul>
         *   <li>String：调用 redactStrategyCodes 替换策略编码</li>
         *   <li>List：递归处理每个元素</li>
         *   <li>Map：递归处理每个键值对</li>
         *   <li>其他：直接返回</li>
         * </ul>
         *
         * @param value 待脱敏的值
         * @return 脱敏后的值
         */
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

        /**
         * 替换字符串中的策略编码为 [REDACTED_STRATEGY]。
         *
         * <p>使用正则表达式匹配 S_ 开头的策略编码并替换。
         * <p>例如：S_PRINCIPLE → [REDACTED_STRATEGY]
         *
         * @param text 原始文本
         * @return 脱敏后的文本
         */
        private String redactStrategyCodes(String text) {
        return STRATEGY_CODE_PATTERN.matcher(text).replaceAll("[REDACTED_STRATEGY]");
    }

        /**
         * 修复结果记录，封装修复操作的完整结果。
         *
         * @param success 是否修复成功
         * @param repairInput 修复输入
         * @param repairedOutput 修复后输出
         * @param plan 修复后的执行计划
         * @param errorCodes 剩余错误列表（如果还有的话）
         */
        public record RepairResult(boolean success,
                                  EvaluationDecisionInput repairInput,
                                  EvaluationDecisionOutput repairedOutput,
                                  DecisionExecutionPlan plan,
                                  List<String> errorCodes) {
        }
}
