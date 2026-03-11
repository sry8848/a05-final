package com.a05.aiinterview.ai.contract;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationOutput;
import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * AI 输出 DTO 契约验证器。
 *
 * <p>职责：对四类核心 AI 调用（Planner / QuestionGeneration / EvaluationDecision / Report）
 * 的输出 DTO 进行必填字段校验和兜底降级处理，确保主链路不因 AI 输出格式漂移而中断。
 *
 * <p>降级策略：
 * <ul>
 *   <li>缺少必填字段 → 填入兜底默认值，同时 {@code log.warn} 打印字段路径</li>
 *   <li>字段类型漂移（JSON 解析异常）→ {@code log.error} 打印完整堆栈，返回最小可用降级对象</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOutputContractValidator {

    private final ObjectMapper objectMapper;

    // ─────────────────────────── Planner ────────────────────────────────────

    /**
     * 验证并修复 Planner 输出契约。
     *
     * <p>必填契约：
     * <ol>
     *   <li>{@code domains} 非空列表</li>
     *   <li>{@code questionMixPlan} 非空 Map</li>
     *   <li>{@code projects} 中每个锚点必须有 {@code projectId} 和 {@code name}</li>
     * </ol>
     *
     * @param output AI 返回的 PlannerOutput，可能含 null 字段
     * @return 经验证修复后的输出，保证必填字段非 null
     */
    public PlannerOutput validatePlanner(PlannerOutput output) {
        if (output == null) {
            log.warn("[契约] Planner 输出为 null，返回最小降级对象");
            return buildFallbackPlannerOutput();
        }

        // 校验 domains
        if (output.getDomains() == null || output.getDomains().isEmpty()) {
            log.warn("[契约] Planner.domains 为空，已填入空列表兜底，请检查 Prompt 或 AI 输出格式");
            output.setDomains(new ArrayList<>());
        }

        // 校验 questionMixPlan
        if (output.getQuestionMixPlan() == null || output.getQuestionMixPlan().isEmpty()) {
            log.warn("[契约] Planner.questionMixPlan 为空，已填入默认配额兜底");
            output.setQuestionMixPlan(buildDefaultQuestionMixPlan());
        }

        // 校验 projects 中关键字段完整性
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

    /**
     * 从 JSON 字符串安全解析 PlannerOutput；解析失败时记录异常并返回降级对象。
     *
     * @param json AI 返回的原始 JSON 文本
     * @return 验证后的输出，类型漂移时返回最小降级对象
     */
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

    // ────────────────────────── QuestionGeneration ──────────────────────────

    /**
     * 验证并修复 QuestionGeneration 输出契约。
     *
     * <p>必填契约：
     * <ol>
     *   <li>{@code stem} 非空非空白</li>
     *   <li>{@code expectedPoints} 非 null（允许空列表）</li>
     * </ol>
     *
     * @param output AI 返回的 QuestionGenerationOutput
     * @return 验证修复后的输出
     */
    public QuestionGenerationOutput validateQuestionGeneration(QuestionGenerationOutput output) {
        if (output == null) {
            log.warn("[契约] QuestionGeneration 输出为 null，返回最小降级对象");
            return buildFallbackQuestionGenerationOutput();
        }

        if (output.getStem() == null || output.getStem().isBlank()) {
            log.warn("[契约] QuestionGeneration.stem 为空，已填入占位符兜底");
            output.setStem("（AI 出题失败，请重试）");
        }

        if (output.getExpectedPoints() == null) {
            log.warn("[契约] QuestionGeneration.expectedPoints 为 null，已填入空列表兜底");
            output.setExpectedPoints(new ArrayList<>());
        }

        return output;
    }

    /**
     * 从 JSON 字符串安全解析 QuestionGenerationOutput；解析失败时降级。
     *
     * @param json AI 返回的原始 JSON 文本
     * @return 验证后的输出
     */
    public QuestionGenerationOutput parseAndValidateQuestionGeneration(String json) {
        try {
            QuestionGenerationOutput output = objectMapper.readValue(json, QuestionGenerationOutput.class);
            return validateQuestionGeneration(output);
        } catch (Exception e) {
            log.error("[契约] QuestionGeneration 输出解析失败，类型漂移或格式异常，已降级, json摘要={}",
                    safeSnippet(json), e);
            return buildFallbackQuestionGenerationOutput();
        }
    }

    // ────────────────────────── EvaluationDecision ──────────────────────────

    /**
     * 验证并修复 EvaluationDecision 输出契约。
     *
     * <p>必填契约：
     * <ol>
     *   <li>{@code signal} 非空（NEXT_DOMAIN / DEEPEN / END 之一）</li>
     *   <li>{@code patch}（ledgerPatch）非 null</li>
     *   <li>signal != END 时 {@code nextStrategy} 非 null</li>
     * </ol>
     *
     * @param output AI 返回的 EvaluationDecisionOutput
     * @return 验证修复后的输出
     */
    public EvaluationDecisionOutput validateEvaluationDecision(EvaluationDecisionOutput output) {
        if (output == null) {
            log.warn("[契约] EvaluationDecision 输出为 null，返回最小降级对象");
            return buildFallbackEvaluationDecisionOutput();
        }

        if (output.getSignal() == null || output.getSignal().isBlank()) {
            log.warn("[契约] EvaluationDecision.signal 为空，已兜底为 NEXT_DOMAIN");
            output = EvaluationDecisionOutput.builder()
                    .domainCode(output.getDomainCode())
                    .depthReached(output.getDepthReached())
                    .saturated(output.isSaturated())
                    .signal("NEXT_DOMAIN")
                    .patch(output.getPatch())
                    .nextStrategy(output.getNextStrategy())
                    .reasoning(output.getReasoning())
                    .build();
        }

        if (output.getPatch() == null) {
            log.warn("[契约] EvaluationDecision.patch(ledgerPatch) 为 null，已填入最小 patch 兜底");
            EvaluationDecisionOutput.LedgerPatch emptyPatch = EvaluationDecisionOutput.LedgerPatch.builder()
                    .domainCode(output.getDomainCode())
                    .build();
            output.setPatch(emptyPatch);
        }

        // signal 非 END 时检查 nextStrategy
        if (!"END".equalsIgnoreCase(output.getSignal()) && output.getNextStrategy() == null) {
            log.warn("[契约] EvaluationDecision.signal={} 但 nextStrategy 为 null，将降级为 END 信号",
                    output.getSignal());
            output.setSignal("END");
        }

        return output;
    }

    /**
     * 从 JSON 字符串安全解析 EvaluationDecisionOutput；解析失败时降级。
     *
     * @param json AI 返回的原始 JSON 文本
     * @return 验证后的输出
     */
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

    // ─────────────────────────── Report ────────────────────────────────────

    /**
     * 验证并修复 ReportGeneration 输出契约。
     *
     * <p>必填契约：
     * <ol>
     *   <li>{@code overallScore} 在 [0, 100] 范围内</li>
     *   <li>{@code skillDomainScores} 非 null</li>
     *   <li>{@code summary} 非空</li>
     * </ol>
     *
     * @param output AI 返回的 ReportGenerationOutput
     * @return 验证修复后的输出
     */
    public ReportGenerationOutput validateReport(ReportGenerationOutput output) {
        if (output == null) {
            log.warn("[契约] Report 输出为 null，返回最小降级对象");
            return buildFallbackReportOutput();
        }

        // 校验 overallScore 范围
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

        if (output.getSummary() == null || output.getSummary().isBlank()) {
            log.warn("[契约] Report.summary 为空，已填入占位符兜底");
            output.setSummary("（报告生成失败，请重试）");
        }

        return output;
    }

    /**
     * 从 JSON 字符串安全解析 ReportGenerationOutput；解析失败时降级。
     *
     * @param json AI 返回的原始 JSON 文本
     * @return 验证后的输出
     */
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

    // ──────────────────────────── 降级构造 ────────────────────────────────

    private PlannerOutput buildFallbackPlannerOutput() {
        PlannerOutput fallback = new PlannerOutput();
        fallback.setTitle("（考纲生成失败）");
        fallback.setDomains(new ArrayList<>());
        fallback.setQuestionMixPlan(buildDefaultQuestionMixPlan());
        fallback.setProjects(new ArrayList<>());
        return fallback;
    }

    private QuestionGenerationOutput buildFallbackQuestionGenerationOutput() {
        QuestionGenerationOutput fallback = new QuestionGenerationOutput();
        fallback.setStem("（AI 出题失败，请重试）");
        fallback.setExpectedPoints(new ArrayList<>());
        return fallback;
    }

    private EvaluationDecisionOutput buildFallbackEvaluationDecisionOutput() {
        return EvaluationDecisionOutput.builder()
                .signal("END")
                .reasoning("[降级] AI 评估决策解析失败，强制结束本轮面试")
                .build();
    }

    private ReportGenerationOutput buildFallbackReportOutput() {
        return ReportGenerationOutput.builder()
                .overallScore(BigDecimal.ZERO)
                .summary("（报告生成失败，请联系管理员）")
                .strengths(new ArrayList<>())
                .weaknesses(new ArrayList<>())
                .improvementSuggestions(new ArrayList<>())
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

    /**
     * 截取 JSON 前 200 字符用于日志摘要，避免超长日志。
     */
    private String safeSnippet(String json) {
        if (json == null) return "null";
        return json.length() > 200 ? json.substring(0, 200) + "..." : json;
    }
}
