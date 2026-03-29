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
import java.util.List;
import java.util.Locale;

/**
 * AI 输出 DTO 契约验证器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOutputContractValidator {

    private static final List<String> ALLOWED_INTERVIEW_ACTIONS = List.of("CONTINUE", "WRAPUP");
    private static final String DEFAULT_PLANNING_REASONING = "按岗位、简历与真实经历规划本次考纲。";

    private final ObjectMapper objectMapper;

    public PlannerOutput validatePlanner(PlannerOutput output) {
        if (output == null) {
            log.warn("[契约] Planner 输出为 null，返回最小降级对象");
            return buildFallbackPlannerOutput();
        }
        if (output.getPlanningReasoning() == null || output.getPlanningReasoning().isBlank()) {
            output.setPlanningReasoning(DEFAULT_PLANNING_REASONING);
        }
        if (output.getDomains() == null) {
            output.setDomains(new ArrayList<>());
        }
        if (output.getExperienceItems() == null) {
            output.setExperienceItems(new ArrayList<>());
        }
        output.getDomains().removeIf(domain -> domain == null
                || isBlank(domain.getDomainCode())
                || isBlank(domain.getDomainName()));
        output.getDomains().forEach(domain -> {
            if (domain.getFocusPoints() == null) {
                domain.setFocusPoints(new ArrayList<>());
            } else {
                domain.setFocusPoints(sanitizeStringList(domain.getFocusPoints()));
            }
        });
        output.getExperienceItems().removeIf(item -> item == null
                || isBlank(item.getItemType())
                || isBlank(item.getItemName()));
        output.getExperienceItems().forEach(item -> {
            item.setItemType(item.getItemType().trim().toUpperCase(Locale.ROOT));
            if (item.getResumeDescription() == null) {
                item.setResumeDescription("");
            }
            if (item.getTechHooks() == null) {
                item.setTechHooks(new ArrayList<>());
            } else {
                item.setTechHooks(sanitizeStringList(item.getTechHooks()));
            }
        });
        return output;
    }

    public PlannerOutput parseAndValidatePlanner(String json) {
        try {
            return validatePlanner(objectMapper.readValue(json, PlannerOutput.class));
        } catch (Exception e) {
            log.error("[契约] Planner 输出解析失败，已降级, json摘要={}", safeSnippet(json), e);
            return buildFallbackPlannerOutput();
        }
    }

    public EvaluationDecisionOutput validateEvaluationDecision(EvaluationDecisionOutput output) {
        if (output == null) {
            log.warn("[契约] EvaluationDecision 输出为 null，返回最小降级对象");
            return buildFallbackEvaluationDecisionOutput();
        }

        output.setDecisionReason(defaultString(output.getDecisionReason(), ""));
        output.setFinalDecision(defaultString(output.getFinalDecision(), ""));
        output.setNextFocus(defaultString(output.getNextFocus(), ""));
        output.setNextItemType(defaultString(output.getNextItemType(), ""));
        output.setNextItemName(defaultString(output.getNextItemName(), ""));
        output.setNextProjectPoint(defaultString(output.getNextProjectPoint(), ""));
        output.setTargetDomainCode(defaultString(output.getTargetDomainCode(), ""));
        List<EvaluationDecisionOutput.CoveredDomain> rawCoveredDomains = output.getNewCoveredDomains();
        output.setNewCoveredDomains(sanitizeCoveredDomains(rawCoveredDomains));
        output.setNewCoveredPoints(sanitizeStringList(output.getNewCoveredPoints()));
        output.setRetrievalPlans(sanitizeRetrievalPlans(output.getRetrievalPlans()));

        String normalizedInterviewAction = normalizeInterviewAction(output.getInterviewAction());
        if (normalizedInterviewAction == null) {
            log.warn("[契约] EvaluationDecision.interviewAction 非法，降级为 WRAPUP");
            return buildFallbackEvaluationDecisionOutput();
        }
        output.setInterviewAction(normalizedInterviewAction);

        if (!StrategyCatalog.isAllowed(output.getFinalDecision())) {
            log.warn("[契约] EvaluationDecision.finalDecision 非法，降级为 WRAPUP");
            return buildFallbackEvaluationDecisionOutput();
        }

        if ("WRAPUP".equals(output.getInterviewAction())) {
            output.setFinalDecision(StrategyCode.S_WRAPUP.code());
            output.setNextFocus("");
            output.setNextItemType("");
            output.setNextItemName("");
            output.setNextProjectPoint("");
            output.setTargetDomainCode("");
            output.setRetrievalPlans(new ArrayList<>());
            return output;
        }

        if (StrategyCatalog.isWrapup(output.getFinalDecision())) {
            log.warn("[契约] EvaluationDecision.CONTINUE 不允许使用 S_WRAPUP，降级为 WRAPUP");
            return buildFallbackEvaluationDecisionOutput();
        }
        if (isBlank(output.getNextFocus())) {
            log.warn("[契约] EvaluationDecision.nextFocus 为空，降级为 WRAPUP");
            return buildFallbackEvaluationDecisionOutput();
        }
        if (rawCoveredDomains != null && !rawCoveredDomains.isEmpty()
                && output.getNewCoveredDomains().size() != rawCoveredDomains.size()) {
            log.warn("[契约] EvaluationDecision.newCoveredDomains 必须使用 domainCode，降级为 WRAPUP");
            return buildFallbackEvaluationDecisionOutput();
        }
        if (StrategyCatalog.requiresTargetDomain(output.getFinalDecision())) {
            if (isBlank(output.getTargetDomainCode())) {
                log.warn("[契约] EvaluationDecision.targetDomainCode 缺失，降级为 WRAPUP");
                return buildFallbackEvaluationDecisionOutput();
            }
        } else {
            output.setTargetDomainCode("");
        }

        if (isBlank(StrategyCatalog.targetQuestionType(output.getFinalDecision()))) {
            log.warn("[契约] EvaluationDecision.finalDecision 无法映射为下一题题型，降级为 WRAPUP");
            return buildFallbackEvaluationDecisionOutput();
        }
        return output;
    }

    public EvaluationDecisionOutput parseAndValidateEvaluationDecision(String json) {
        try {
            return validateEvaluationDecision(objectMapper.readValue(json, EvaluationDecisionOutput.class));
        } catch (Exception e) {
            log.error("[契约] EvaluationDecision 输出解析失败，已降级, json摘要={}", safeSnippet(json), e);
            return buildFallbackEvaluationDecisionOutput();
        }
    }

    public ReportGenerationOutput validateReport(ReportGenerationOutput output) {
        if (output == null) {
            log.warn("[契约] Report 输出为 null，返回最小降级对象");
            return buildFallbackReportOutput();
        }
        output.setOverallScore(clampScore(output.getOverallScore(), BigDecimal.ZERO));
        if (output.getSummary() == null || output.getSummary().isBlank()) {
            output.setSummary("（报告生成失败，请重试）");
        }
        if (output.getStrengths() == null) {
            output.setStrengths(new ArrayList<>());
        }
        if (output.getWeaknesses() == null) {
            output.setWeaknesses(new ArrayList<>());
        }
        if (output.getImprovementSuggestions() == null) {
            output.setImprovementSuggestions(new ArrayList<>());
        }
        if (output.getSkillDomainScores() == null) {
            output.setSkillDomainScores(new ArrayList<>());
        } else {
            output.getSkillDomainScores().forEach(item -> {
                if (item != null) {
                    item.setScore(clampScore(item.getScore(), null));
                }
            });
        }
        if (output.getComprehensiveRadarScores() != null) {
            output.getComprehensiveRadarScores().forEach(item -> {
                if (item != null) {
                    item.setScore(clampScore(item.getScore(), null));
                }
            });
        }
        return output;
    }

    public ReportGenerationOutput parseAndValidateReport(String json) {
        try {
            return validateReport(objectMapper.readValue(json, ReportGenerationOutput.class));
        } catch (Exception e) {
            log.error("[契约] Report 输出解析失败，已降级, json摘要={}", safeSnippet(json), e);
            return buildFallbackReportOutput();
        }
    }

    private PlannerOutput buildFallbackPlannerOutput() {
        return PlannerOutput.builder()
                .planningReasoning("考纲生成失败，已降级为空规划。")
                .domains(new ArrayList<>())
                .experienceItems(new ArrayList<>())
                .build();
    }

    private EvaluationDecisionOutput buildFallbackEvaluationDecisionOutput() {
        return EvaluationDecisionOutput.builder()
                .interviewAction("WRAPUP")
                .decisionReason("")
                .finalDecision(StrategyCode.S_WRAPUP.code())
                .nextFocus("")
                .nextItemType("")
                .nextItemName("")
                .nextProjectPoint("")
                .targetDomainCode("")
                .newCoveredDomains(new ArrayList<>())
                .newCoveredPoints(new ArrayList<>())
                .retrievalPlans(new ArrayList<>())
                .build();
    }

    private ReportGenerationOutput buildFallbackReportOutput() {
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

    private List<String> sanitizeStringList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<EvaluationDecisionOutput.CoveredDomain> sanitizeCoveredDomains(
            List<EvaluationDecisionOutput.CoveredDomain> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(item -> item != null && !isBlank(item.getDomainCode()) && !isBlank(item.getDomainName()))
                .map(item -> EvaluationDecisionOutput.CoveredDomain.builder()
                        .domainCode(item.getDomainCode().trim())
                        .domainName(item.getDomainName().trim())
                        .build())
                .toList();
    }

    private List<EvaluationDecisionOutput.RetrievalPlan> sanitizeRetrievalPlans(
            List<EvaluationDecisionOutput.RetrievalPlan> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(item -> item != null)
                .map(item -> EvaluationDecisionOutput.RetrievalPlan.builder()
                        .goal(defaultString(item.getGoal(), ""))
                        .displayQuery(defaultString(item.getDisplayQuery(), ""))
                        .queryText(defaultString(item.getQueryText(), ""))
                        .keywordHints(sanitizeStringList(item.getKeywordHints()))
                        .difficultyHint(defaultString(item.getDifficultyHint(), ""))
                        .mustHaveClues(sanitizeStringList(item.getMustHaveClues()))
                        .avoidClues(sanitizeStringList(item.getAvoidClues()))
                        .build())
                .toList();
    }

    private String normalizeInterviewAction(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String normalized = action.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_INTERVIEW_ACTIONS.contains(normalized) ? normalized : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultString(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private String safeSnippet(String json) {
        if (json == null) {
            return "null";
        }
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
    }

    private BigDecimal clampScore(BigDecimal score, BigDecimal fallbackWhenNull) {
        if (score == null) {
            return fallbackWhenNull;
        }
        return score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
    }
}
