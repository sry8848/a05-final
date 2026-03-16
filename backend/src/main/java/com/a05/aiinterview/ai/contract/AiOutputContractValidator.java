package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * AI 输出 DTO 契约验证器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOutputContractValidator {

    private final ObjectMapper objectMapper;

    public PlannerOutput validatePlanner(PlannerOutput output) {
        if (output == null) {
            log.warn("[契约] Planner 输出为 null，返回最小降级对象");
            return buildFallbackPlannerOutput();
        }
        if (output.getDomains() == null || output.getDomains().isEmpty()) {
            log.warn("[契约] Planner.domains 为空，已填入空列表兜底，请检查 Prompt 或 AI 输出格式");
            output.setDomains(new ArrayList<>());
        }
        if (output.getQuestionMixPlan() == null || output.getQuestionMixPlan().isEmpty()) {
            log.warn("[契约] Planner.questionMixPlan 为空，已填入默认配额兜底");
            output.setQuestionMixPlan(buildDefaultQuestionMixPlan());
        }
        if (output.getProjects() != null) {
            for (PlannerOutput.ProjectAnchor anchor : output.getProjects()) {
                if (anchor.getProjectId() == null || anchor.getProjectId().isBlank()) {
                    log.warn("[契约] Planner.projects 中存在 projectId 为空的锚点，已跳过补全");
                }
                if (anchor.getName() == null || anchor.getName().isBlank()) {
                    log.warn("[契约] Planner.projects 中存在 name 为空的锚点，已跳过补全");
                }
            }
        }
        return output;
    }

    public PlannerOutput parseAndValidatePlanner(String json) {
        try {
            PlannerOutput output = objectMapper.readValue(json, PlannerOutput.class);
            return validatePlanner(output);
        } catch (Exception e) {
            log.error("[契约] Planner 输出解析失败，类型漂移或格式异常，已降级, json摘要={}",
                    safeSnippet(json), e);
            return buildFallbackPlannerOutput();
        }
    }

    public EvaluationDecisionOutput validateEvaluationDecision(EvaluationDecisionOutput output) {
        if (output == null) {
            log.warn("[契约] EvaluationDecision 输出为 null，返回最小降级对象");
            return buildFallbackEvaluationDecisionOutput();
        }

        String signal = normalizeSignal(output.getSignal());
        if (signal == null) {
            log.warn("[契约] EvaluationDecision.signal 非法，降级为 END");
            return buildFallbackEvaluationDecisionOutput();
        }
        output.setSignal(signal);

        if ("END".equals(signal)) {
            output.setDeepen(false);
            output.setNextStrategy(null);
            return output;
        }

        if (output.getNextStrategy() == null) {
            log.warn("[契约] signal={} 但 nextStrategy 为空，降级为 END", signal);
            return buildFallbackEvaluationDecisionOutput();
        }

        sanitizeNextStrategy(output.getNextStrategy());

        switch (signal) {
            case "DEEPEN" -> {
                if (!output.isPassCurrentLevel()) {
                    log.warn("[契约] signal=DEEPEN 但 passCurrentLevel=false，降级为 NEXT_DOMAIN");
                    output.setSignal("NEXT_DOMAIN");
                    output.setDeepen(false);
                } else {
                    output.setDeepen(true);
                }
            }
            case "RETRY_SAME_DOMAIN" -> {
                if (output.isPassCurrentLevel()) {
                    log.warn("[契约] signal=RETRY_SAME_DOMAIN 但 passCurrentLevel=true，已回收为未通过当前层");
                    output.setPassCurrentLevel(false);
                }
                output.setDeepen(false);
            }
            default -> {
                if (output.isDeepen()) {
                    log.warn("[契约] deepen=true 但 signal!=DEEPEN，已回收 deepen 标记");
                    output.setDeepen(false);
                }
            }
        }

        return output;
    }

    public EvaluationDecisionOutput parseAndValidateEvaluationDecision(String json) {
        try {
            EvaluationDecisionOutput output = objectMapper.readValue(json, EvaluationDecisionOutput.class);
            return validateEvaluationDecision(output);
        } catch (Exception e) {
            log.error("[契约] EvaluationDecision 输出解析失败，类型漂移或格式异常，已降级, json摘要={}",
                    safeSnippet(json), e);
            return buildFallbackEvaluationDecisionOutput();
        }
    }

    public ReportGenerationOutput validateReport(ReportGenerationOutput output) {
        if (output == null) {
            log.warn("[契约] Report 输出为 null，返回最小降级对象");
            return buildFallbackReportOutput();
        }

        if (output.getOverallScore() == null) {
            log.warn("[契约] Report.overallScore 为 null，已兜底为 0");
            output.setOverallScore(BigDecimal.ZERO);
        } else {
            BigDecimal score = output.getOverallScore();
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
                log.warn("[契约] Report.overallScore={} 超出 [0,100] 范围，已截断修正", score);
                output.setOverallScore(score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)));
            }
        }

        if (output.getSkillDomainScores() == null) {
            log.warn("[契约] Report.skillDomainScores 为 null，已填入空列表兜底");
            output.setSkillDomainScores(new ArrayList<>());
        }
        if (output.getComprehensiveRadarScores() != null) {
            output.setComprehensiveRadarScores(output.getComprehensiveRadarScores().stream()
                    .filter(item -> item != null
                            && item.getDimensionKey() != null
                            && !item.getDimensionKey().isBlank()
                            && item.getDimensionName() != null
                            && !item.getDimensionName().isBlank()
                            && item.getScore() != null)
                    .peek(item -> {
                        BigDecimal score = item.getScore();
                        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
                            item.setScore(score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)));
                        }
                    })
                    .toList());
        }

        if (output.getSummary() == null || output.getSummary().isBlank()) {
            log.warn("[契约] Report.summary 为空，已填入占位符兜底");
            output.setSummary("（报告生成失败，请重试）");
        }

        return output;
    }

    public ReportGenerationOutput parseAndValidateReport(String json) {
        try {
            ReportGenerationOutput output = objectMapper.readValue(json, ReportGenerationOutput.class);
            return validateReport(output);
        } catch (Exception e) {
            log.error("[契约] Report 输出解析失败，类型漂移或格式异常，已降级, json摘要={}",
                    safeSnippet(json), e);
            return buildFallbackReportOutput();
        }
    }

    private void sanitizeNextStrategy(EvaluationDecisionOutput.NextQuestionStrategy strategy) {
        strategy.setQuestionType(normalizeQuestionType(strategy.getQuestionType()));
        strategy.setTargetDepth(normalizeDepth(strategy.getTargetDepth()));
        strategy.setDifficulty(normalizeDepth(strategy.getDifficulty()));
        String focus = sanitizeSingleFocus(firstNonBlank(
                strategy.getFocusPoint(),
                strategy.getTargetSkill(),
                strategy.getNextDomainName(),
                strategy.getNextDomainCode()
        ));
        strategy.setFocusPoint(focus);
        if (strategy.getTargetSkill() == null || strategy.getTargetSkill().isBlank()) {
            strategy.setTargetSkill(focus);
        } else {
            strategy.setTargetSkill(sanitizeSingleFocus(strategy.getTargetSkill()));
        }
        List<String> expectedPoints = strategy.getExpectedPoints();
        if (expectedPoints == null || expectedPoints.isEmpty()) {
            strategy.setExpectedPoints(List.of(
                    "说明" + focus + "的核心概念",
                    "结合场景说明" + focus + "的使用方式"
            ));
            return;
        }
        List<String> sanitized = new ArrayList<>();
        for (String point : expectedPoints) {
            if (point == null || point.isBlank()) {
                continue;
            }
            String normalized = point.trim();
            if (containsAnotherFocus(normalized, focus)) {
                continue;
            }
            if (!normalized.contains(focus)) {
                sanitized.add("围绕" + focus + "说明：" + normalized);
            } else {
                sanitized.add(normalized);
            }
        }
        if (sanitized.isEmpty()) {
            sanitized = List.of(
                    "说明" + focus + "的核心概念",
                    "比较" + focus + "的常见方案与取舍",
                    "结合场景说明" + focus + "的选择依据"
            );
        }
        strategy.setExpectedPoints(sanitized.stream().limit(5).toList());
    }

    private boolean containsAnotherFocus(String text, String focus) {
        if (focus == null || focus.isBlank()) {
            return false;
        }
        String normalizedFocus = focus.toLowerCase(Locale.ROOT);
        String normalizedText = text.toLowerCase(Locale.ROOT);
        if (normalizedText.contains(normalizedFocus)) {
            return false;
        }
        return normalizedText.contains("seata") || normalizedText.contains("tcc") || normalizedText.contains("saga");
    }

    private String normalizeSignal(String signal) {
        if (signal == null || signal.isBlank()) {
            return null;
        }
        String normalized = signal.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DEEPEN", "RETRY_SAME_DOMAIN", "NEXT_DOMAIN", "END" -> normalized;
            default -> null;
        };
    }

    private String normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return "PRINCIPLE";
        }
        return questionType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDepth(String depth) {
        if (depth == null || depth.isBlank()) {
            return "L2";
        }
        String normalized = depth.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("L[1-5]") ? normalized : "L2";
    }

    private String sanitizeSingleFocus(String value) {
        if (value == null || value.isBlank()) {
            return "基础能力";
        }
        String[] parts = value.split("(?i)\\band\\b|\\bor\\b|\\bvs\\b|与|和|及|以及|、|/|\\+|,|，|;|；|\\|");
        String focus = parts.length > 0 ? parts[0].trim() : value.trim();
        return focus.isBlank() ? "基础能力" : focus;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "基础能力";
    }

    private PlannerOutput buildFallbackPlannerOutput() {
        log.info("========== 兜底题提示 ==========");
        log.info("【考纲生成失败】AI 返回数据异常，已启用兜底考纲");
        log.info("请检查：1. AI 模型是否正常 2. Prompt 配置是否正确");
        log.info("=================================");
        PlannerOutput fallback = new PlannerOutput();
        fallback.setTitle("（考纲生成失败）");
        fallback.setDomains(new ArrayList<>());
        fallback.setQuestionMixPlan(buildDefaultQuestionMixPlan());
        fallback.setProjects(new ArrayList<>());
        return fallback;
    }

    private EvaluationDecisionOutput buildFallbackEvaluationDecisionOutput() {
        log.info("========== 兜底题提示 ==========");
        log.info("【评估决策失败】AI 返回数据异常，已强制结束面试");
        log.info("请检查：1. AI 模型是否正常 2. 评估决策 Prompt 配置");
        log.info("=================================");
        return EvaluationDecisionOutput.builder()
                .passCurrentLevel(false)
                .deepen(false)
                .signal("END")
                .reasoning("[降级] AI 评估决策解析失败，强制结束本轮面试")
                .build();
    }

    private ReportGenerationOutput buildFallbackReportOutput() {
        log.info("========== 兜底题提示 ==========");
        log.info("【报告生成失败】AI 返回数据异常，已生成兜底报告");
        log.info("请检查：1. AI 模型是否正常 2. 报告生成 Prompt 配置");
        log.info("=================================");
        return ReportGenerationOutput.builder()
                .overallScore(BigDecimal.ZERO)
                .summary("（报告生成失败，请联系管理员）")
                .strengths(new ArrayList<>())
                .weaknesses(new ArrayList<>())
                .improvementSuggestions(new ArrayList<>())
                .comprehensiveRadarScores(null)
                .skillDomainScores(new ArrayList<>())
                .build();
    }

    private java.util.Map<String, Integer> buildDefaultQuestionMixPlan() {
        java.util.Map<String, Integer> plan = new HashMap<>();
        plan.put("INTRO", 1);
        plan.put("PROJECT_DEEP_DIVE", 2);
        plan.put("SCENARIO", 2);
        plan.put("PRINCIPLE", 3);
        plan.put("BEHAVIORAL", 1);
        return plan;
    }

    private String safeSnippet(String json) {
        if (json == null) {
            return "null";
        }
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
    }
}
