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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        if (output.getDecision() == null || output.getDecision().isBlank()) {
            log.warn("[契约] EvaluationDecision 缺少新 decision，旧 signal 已不再接受，降级为 wrapup");
            return buildFallbackEvaluationDecisionOutput();
        }
        return validateNewEvaluationDecision(output);
    }

    private EvaluationDecisionOutput validateNewEvaluationDecision(EvaluationDecisionOutput output) {
        String decision = normalizeDecision(output.getDecision());
        if (decision == null) {
            log.warn("[契约] EvaluationDecision.decision 非法，降级为 wrapup");
            return buildFallbackEvaluationDecisionOutput();
        }
        output.setDecision(decision);
        output.setAnswerAssessment(trimToNull(output.getAnswerAssessment()));
        output.setAnswerVerdict(normalizeVerdict(output.getAnswerVerdict()));
        output.setTargetAngle(normalizeAngle(output.getTargetAngle()));
        output.setDifficultyAdjustment(normalizeDifficultyAdjustment(output.getDifficultyAdjustment()));
        output.setDomainOutcome(normalizeDomainOutcome(output.getDomainOutcome(), decision));
        output.setStatePatch(sanitizeStatePatch(output.getStatePatch()));
        String targetFocus = trimToNull(output.getTargetFocus());
        String focusPoint = trimToNull(output.getFocusPoint());
        output.setTargetFocus(firstNonBlank(targetFocus, focusPoint));
        output.setFocusPoint(firstNonBlank(focusPoint, targetFocus));
        output.setQuestionType(normalizeQuestionType(output.getQuestionType()));
        output.setNextQuestionGoal(trimToNull(output.getNextQuestionGoal()));
        output.setNextDomainCode(trimToNull(output.getNextDomainCode()));
        output.setNextDomainName(trimToNull(output.getNextDomainName()));
        output.setRetrievalIntent(sanitizeRetrievalIntent(output.getRetrievalIntent(), output.getFocusPoint(), output.getQuestionType()));
        output.setTags(sanitizeTags(output.getTags()));

        if (output.getAnswerAssessment() == null) {
            log.warn("[契约] EvaluationDecision.answerAssessment 为空，降级为 wrapup");
            return buildFallbackEvaluationDecisionOutput();
        }

        if ("wrapup".equals(decision)) {
            clearNextQuestionFields(output);
            return output;
        }

        if (output.getNextQuestionGoal() == null
                || output.getTargetFocus() == null
                || output.getFocusPoint() == null) {
            log.warn("[契约] EvaluationDecision 缺少下一问关键字段，降级为 wrapup");
            return buildFallbackEvaluationDecisionOutput();
        }

        if (output.getNextDomainCode() == null && !"INTRO".equalsIgnoreCase(output.getQuestionType())) {
            log.warn("[契约] EvaluationDecision.nextDomainCode 为空，降级为 wrapup");
            return buildFallbackEvaluationDecisionOutput();
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
        String focus = firstNonBlank(
                trimToNull(strategy.getFocusPoint()),
                trimToNull(strategy.getTargetSkill()),
                trimToNull(strategy.getNextDomainName()),
                trimToNull(strategy.getNextDomainCode())
        );
        if (focus == null) {
            focus = "基础能力";
        }
        strategy.setFocusPoint(focus);
        if (strategy.getTargetSkill() == null || strategy.getTargetSkill().isBlank()) {
            strategy.setTargetSkill(focus);
        } else {
            strategy.setTargetSkill(trimToNull(strategy.getTargetSkill()));
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

    private EvaluationDecisionOutput.RetrievalIntent sanitizeRetrievalIntent(
            EvaluationDecisionOutput.RetrievalIntent retrievalIntent,
            String focusPoint,
            String questionType) {
        if (retrievalIntent == null) {
            return null;
        }
        retrievalIntent.setDomainHint(trimToNull(retrievalIntent.getDomainHint()));
        retrievalIntent.setFocusQuery(firstNonBlank(
                trimToNull(retrievalIntent.getFocusQuery()),
                trimToNull(focusPoint)
        ));
        retrievalIntent.setQuestionTypeHint(normalizeQuestionType(firstNonBlank(
                retrievalIntent.getQuestionTypeHint(),
                questionType
        )));
        List<String> families = retrievalIntent.getAvoidRecentFamilies();
        if (families == null) {
            retrievalIntent.setAvoidRecentFamilies(List.of());
        } else {
            retrievalIntent.setAvoidRecentFamilies(families.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .toList());
        }
        return retrievalIntent;
    }

    private EvaluationDecisionOutput.Tags sanitizeTags(EvaluationDecisionOutput.Tags tags) {
        if (tags == null) {
            return null;
        }
        tags.setQuestionFamilyHint(trimToNull(tags.getQuestionFamilyHint()));
        tags.setInterviewerIntent(trimToNull(tags.getInterviewerIntent()));
        return tags;
    }

    private void clearNextQuestionFields(EvaluationDecisionOutput output) {
        output.setNextDomainId(null);
        output.setNextDomainCode(null);
        output.setNextDomainName(null);
        output.setQuestionType(null);
        output.setFocusPoint(null);
        output.setRetrievalIntent(null);
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

    private String normalizeDecision(String decision) {
        if (decision == null || decision.isBlank()) {
            return null;
        }
        String normalized = decision.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "followup", "probe", "rescue", "broaden", "wrapup" -> normalized;
            default -> null;
        };
    }

    private String normalizeVerdict(String verdict) {
        if (verdict == null || verdict.isBlank()) {
            return "WEAK";
        }
        String normalized = verdict.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STRONG", "PARTIAL", "WEAK" -> normalized;
            default -> "WEAK";
        };
    }

    private String normalizeAngle(String angle) {
        if (angle == null || angle.isBlank()) {
            return "implementation";
        }
        String normalized = angle.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "implementation", "tradeoff", "boundary", "troubleshooting", "role" -> normalized;
            default -> "implementation";
        };
    }

    private String normalizeDifficultyAdjustment(String adjustment) {
        if (adjustment == null || adjustment.isBlank()) {
            return "same";
        }
        String normalized = adjustment.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "up", "same", "down" -> normalized;
            default -> "same";
        };
    }

    private String normalizeDomainOutcome(String domainOutcome, String decision) {
        if (domainOutcome == null || domainOutcome.isBlank()) {
            return "wrapup".equals(decision) ? "covered" : "continue";
        }
        String normalized = domainOutcome.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "continue", "covered", "circuit_broken" -> normalized;
            default -> "wrapup".equals(decision) ? "covered" : "continue";
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

    private Map<String, Object> sanitizeStatePatch(Map<String, Object> statePatch) {
        if (statePatch == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(statePatch);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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
                .answerAssessment("[降级] AI 评估决策解析失败，强制进入收束")
                .answerVerdict("WEAK")
                .decision("wrapup")
                .targetFocus("综合收束")
                .targetAngle("role")
                .difficultyAdjustment("same")
                .nextQuestionGoal("结束面试并进入总结")
                .domainOutcome("covered")
                .statePatch(new LinkedHashMap<>())
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
