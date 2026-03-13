package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.*;
import com.a05.aiinterview.common.enums.DomainStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 客户端 Mock 实现。
 * 当配置 {@code ai.openai.mock-enabled=true}（默认）时激活，返回硬编码的固定结果。
 * 用途：在没有 API Key 的情况下联调前端，验证接口协议和数据流转。
 *
 * <p>所有方法返回 {@link AiCallResult}，Token 计数均填 0，latencyMs 为实际耗时（无网络）。
 * 切换到真实 AI：将环境变量 {@code AI_MOCK_ENABLED=false} 即可注入 {@link OpenAiClient}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.openai.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final PromptProperties promptProperties;

    // ───────────────────────────── Planner ──────────────────────────────────

    @Override
    public AiCallResult<PlannerOutput> callPlanner(PlannerInput input) {
        log.info("[MockAI] callPlanner, positionCode={}, experienceLevel={}",
                input.getPositionCode(), input.getExperienceLevel());
        long startMs = System.currentTimeMillis();

        PlannerOutput output = new PlannerOutput();
        output.setTitle(input.getPositionName() + " 模拟面试");
        output.setQuestionMixPlan(Map.of(
                "INTRO", 1, "PROJECT_DEEP_DIVE", 2, "SCENARIO", 2,
                "PRINCIPLE", 3, "BEHAVIORAL", 1));
        output.setFocusAreas(List.of("系统设计", "并发编程"));

        // 根据传入的 domains 生成对应的考察计划（最多取前 5 个）
        if (input.getDomains() != null && !input.getDomains().isEmpty()) {
            output.setDomains(input.getDomains().stream()
                    .limit(5)
                    .map(d -> {
                        PlannerOutput.DomainPlan plan = new PlannerOutput.DomainPlan();
                        plan.setDomainId(d.getDomainId());
                        plan.setDomainCode(d.getDomainCode());
                        plan.setDomainName(d.getDomainName());
                        plan.setTargetDepth("L3");
                        plan.setPriority("medium");
                        plan.setFocusPoints(List.of());
                        return plan;
                    })
                    .toList());
        } else {
            output.setDomains(List.of());
        }
        output.setProjects(List.of());

        log.info("[MockAI] callPlanner 完成，共规划 {} 个知识域", output.getDomains().size());
        return mockResult(output, startMs, "planner");
    }

    /**
     * 流式出题：将 Mock 题目文本按字符逐个发出，模拟打字机效果（间隔 30ms）。
     */
    @Override
    public Flux<String> callQuestionGenerationStream(QuestionGenerationInput input) {
        log.info("[MockAI] callQuestionGenerationStream, domainCode={}", input.getNextDomainCode());
        String stem = buildMockStem(input.getNextDomainName(), input.getNextQuestionType());
        return Flux.fromArray(stem.split(""))
                .delayElements(Duration.ofMillis(30));
    }

    @Override
    public AiCallResult<String> callIntroRewrite(IntroRewriteInput input) {
        log.info("[MockAI] callIntroRewrite, positionCode={}, experienceLevel={}",
                input.getPositionCode(), input.getExperienceLevel());
        long startMs = System.currentTimeMillis();

        String rewritten = buildMockIntroRewrite(input);
        return mockResult(rewritten, startMs, "intro_rewrite");
    }

    // ────────────────────────── EvaluationDecision ──────────────────────────

    @Override
    public AiCallResult<EvaluationDecisionOutput> callEvaluationDecision(EvaluationDecisionInput input) {
        log.info("[MockAI] callEvaluationDecision, domainCode={}, questionType={}, targetDepth={}",
                input.getCurrentDomainCode(), input.getCurrentQuestionType(), input.getCurrentTargetDepth());
        long startMs = System.currentTimeMillis();

        String depthReached = input.getCurrentTargetDepth() != null ? input.getCurrentTargetDepth() : "L3";

        EvaluationDecisionOutput.NextQuestionStrategy nextStrategy =
                resolveNextStrategyFromLedger(input);
        String signal = nextStrategy != null ? "NEXT_DOMAIN" : "END";

        EvaluationDecisionOutput.LedgerPatch patch = EvaluationDecisionOutput.LedgerPatch.builder()
                .domainCode(input.getCurrentDomainCode())
                .domainId(input.getCurrentDomainId())
                .currentDepth(depthReached)
                .domainStatus(DomainStatus.COVERED)
                .saturated(true)
                .questionType(input.getCurrentQuestionType())
                .build();

        log.info("[MockAI] callEvaluationDecision 完成, signal={}, nextDomain={}",
                signal, nextStrategy != null ? nextStrategy.getNextDomainCode() : "N/A");

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .domainCode(input.getCurrentDomainCode())
                .depthReached(depthReached)
                .saturated(true)
                .signal(signal)
                .patch(patch)
                .nextStrategy(nextStrategy)
                .reasoning("[Mock] 候选人回答达标，进入下一知识域。")
                .build();

        return mockResult(output, startMs, "evaluation_decision");
    }

    // ─────────────────────────── ReportGeneration ───────────────────────────

    @Override
    public AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input) {
        log.info("[MockAI] callReportGeneration, positionCode={}, qaCount={}",
                input.getPositionCode(),
                input.getQuestionAnswerPairs() != null ? input.getQuestionAnswerPairs().size() : 0);
        long startMs = System.currentTimeMillis();

        List<ReportGenerationOutput.SkillDomainScore> domainScores = buildMockDomainScores(input);
        BigDecimal overallScore = domainScores.isEmpty()
                ? BigDecimal.valueOf(72.0)
                : domainScores.stream()
                        .map(ReportGenerationOutput.SkillDomainScore::getScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(domainScores.size()), 1, java.math.RoundingMode.HALF_UP);

        log.info("[MockAI] callReportGeneration 完成, overallScore={}, domains={}",
                overallScore, domainScores.size());

        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(overallScore)
                .summary("候选人在本场面试中整体表现良好，基础知识掌握扎实，能够结合实际项目经验阐述技术方案。"
                        + "在高并发场景的系统设计方面还有一定提升空间，建议重点加强分布式事务和缓存一致性相关知识的深度。")
                .strengths(List.of(
                        "基础知识掌握扎实，能准确描述核心原理",
                        "表达逻辑清晰，能结合项目经验举例",
                        "学习能力强，对新技术有一定了解"))
                .weaknesses(List.of(
                        "高并发与分布式场景的实践深度有限",
                        "系统设计时缺乏对非功能性需求的考量",
                        "部分知识点停留在概念层面，缺少源码级理解"))
                .improvementSuggestions(List.of(
                        "建议通过实际项目或开源贡献积累高并发处理经验",
                        "重点学习分布式事务（Seata/TCC）和缓存一致性方案",
                        "阅读 JUC 源码（AQS、ConcurrentHashMap 等）加深底层理解"))
                .skillDomainScores(domainScores)
                .build();

        return mockResult(output, startMs, "report_generation");
    }

    @Override
    public AiCallResult<QuestionDetailEvaluationOutput> callQuestionDetailEvaluation(QuestionDetailEvaluationInput input) {
        log.info("[MockAI] callQuestionDetailEvaluation, questionId={}, domainCode={}",
                input.getQuestionId(), input.getDomainCode());
        long startMs = System.currentTimeMillis();

        String domainCode = input.getDomainCode() != null ? input.getDomainCode() : "unknown";
        String domainName = input.getDomainName() != null ? input.getDomainName() : domainCode;

        QuestionDetailEvaluationOutput output = QuestionDetailEvaluationOutput.builder()
                .score(BigDecimal.valueOf(82.0))
                .commentary("回答覆盖了题目核心方向，结构基本完整。建议补充更明确的指标与边界条件，让论证更有说服力。")
                .strengthPoints(List.of(
                        "能够先给出核心结论，再补充关键实现思路。",
                        "回答中体现了与实际工程场景的关联。"
                ))
                .weakPoints(List.of(
                        "缺少量化结果或指标对比，影响说服力。",
                        "边界条件与失败处理描述不够具体。"
                ))
                .evaluatedDomains(List.of(
                        QuestionDetailEvaluationOutput.EvaluatedDomain.builder()
                                .domainCode(domainCode)
                                .domainName(domainName)
                                .score(BigDecimal.valueOf(82.0))
                                .commentary("基础概念与应用思路较清晰，需补强工程细节。")
                                .build()
                ))
                .highlightedSegments(List.of(
                        QuestionDetailEvaluationOutput.HighlightedSegment.builder()
                                .segment("主要提升了系统性能")
                                .label("strength")
                                .comment("有明确优化方向，建议补充前后指标对比。")
                                .build(),
                        QuestionDetailEvaluationOutput.HighlightedSegment.builder()
                                .segment("大概能扛住高并发")
                                .label("weakness")
                                .comment("建议给出容量评估方法和量化上限。")
                                .build()
                ))
                .idealAnswerOutline(List.of(
                        "先定义问题与目标，明确评价指标。",
                        "分步骤说明方案设计与关键权衡。",
                        "结合真实场景给出结果与复盘。",
                        "补充边界条件、失败处理和优化方向。"
                ))
                .rewrittenAnswer("这题我会先明确目标指标，再说明核心方案、关键权衡和落地步骤。随后用一个真实场景给出结果数据，最后补充边界条件与后续优化方向。")
                .build();

        return mockResult(output, startMs, "question_detail_evaluation");
    }

    // ──────────────────────────── 私有工具 ──────────────────────────────────

    /**
     * 构造 Mock 结果：promptTokens / responseTokens 填 0，latencyMs 取实际耗时。
     */
    private <T> AiCallResult<T> mockResult(T output, long startMs, String promptCode) {
        return AiCallResult.<T>builder()
                .output(output)
                .promptCode(promptCode)
                .promptVersion(promptProperties.resolveVersion(promptCode))
                .promptTokens(0)
                .responseTokens(0)
                .latencyMs(System.currentTimeMillis() - startMs)
                .build();
    }

    /**
     * 根据题型生成不同的 Mock 题目文本。
     */
    private String buildMockStem(String domainName, String questionType) {
        String domain = domainName != null ? domainName : "技术";
        if ("INTRO".equals(questionType)) {
            return "请先做一个简单的自我介绍，重点介绍你的技术背景和最近参与的项目。";
        }
        if ("PROJECT_DEEP_DIVE".equals(questionType)) {
            return "请介绍一个你负责过的技术难度较高的项目，重点说明你在其中解决了什么问题，以及具体的技术方案。";
        }
        if ("BEHAVIORAL".equals(questionType)) {
            return "请描述一次你在团队中主导解决技术难题的经历，重点说明你是如何推动问题解决的。";
        }
        return "请详细介绍一下 " + domain + " 的核心原理，并结合你的项目经验说明实际应用场景和遇到过的挑战。";
    }

    private String buildMockIntroRewrite(IntroRewriteInput input) {
        String base = input.getBasePrompt();
        if (base == null || base.isBlank()) {
            return "请先做一个简短的自我介绍，重点讲讲你的技术背景和最近的项目经历。";
        }
        return base.trim();
    }

    /**
     * 从账本 domain_states 找第一个 UNASKED 域，并从 syllabusJson 补充完整信息。
     */
    private EvaluationDecisionOutput.NextQuestionStrategy resolveNextStrategyFromLedger(
            EvaluationDecisionInput input) {
        if (input.getStateLedger() == null) return null;

        Object domainStatesObj = input.getStateLedger().get("domain_states");
        if (!(domainStatesObj instanceof List<?> domainStates)) return null;

        String nextDomainCode = null;
        for (Object ds : domainStates) {
            if (!(ds instanceof Map<?, ?> dsMap)) continue;
            String code = (String) dsMap.get("domain_id");
            String status = (String) dsMap.get("status");
            if (code == null || Objects.equals(code, input.getCurrentDomainCode())) continue;
            // 账本中状态字符串统一与枚举对比，支持大小写容错
            if (status == null || DomainStatus.UNASKED.getValue().equalsIgnoreCase(status)) {
                nextDomainCode = code;
                break;
            }
        }
        if (nextDomainCode == null) return null;

        Long nextDomainId = null;
        String nextDomainName = nextDomainCode;
        String targetDepth = "L3";

        if (input.getSyllabusJson() != null) {
            Object domainsObj = input.getSyllabusJson().get("domains");
            if (domainsObj instanceof List<?> domains) {
                for (Object d : domains) {
                    if (!(d instanceof Map<?, ?> dMap)) continue;
                    if (Objects.equals(nextDomainCode, dMap.get("domainCode"))) {
                        Object idObj = dMap.get("domainId");
                        if (idObj instanceof Number num) nextDomainId = num.longValue();
                        if (dMap.get("domainName") instanceof String name) nextDomainName = name;
                        if (dMap.get("targetDepth") instanceof String td) targetDepth = td;
                        break;
                    }
                }
            }
        }

        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(nextDomainId)
                .nextDomainCode(nextDomainCode)
                .nextDomainName(nextDomainName)
                .questionType("PRINCIPLE")
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .targetSkill(nextDomainName + " 核心原理")
                .expectedPoints(List.of(
                        "解释核心概念和关键机制",
                        "结合项目场景说明取舍",
                        "说明常见风险与优化方式"))
                .focusPoint(nextDomainName + " 核心原理")
                .build();
    }

    /**
     * 从 Q/A 列表中提取知识域，每域生成模拟评分（75 分）。
     */
    private List<ReportGenerationOutput.SkillDomainScore> buildMockDomainScores(
            ReportGenerationInput input) {
        if (input.getQuestionAnswerPairs() == null) return List.of();

        List<ReportGenerationOutput.SkillDomainScore> scores = new ArrayList<>();
        input.getQuestionAnswerPairs().stream()
                .filter(pair -> pair.getDomainCode() != null && !pair.getDomainCode().isBlank()
                        && !"intro".equalsIgnoreCase(pair.getDomainCode()))
                .collect(java.util.stream.Collectors.toMap(
                        ReportGenerationInput.QuestionAnswerPair::getDomainCode,
                        p -> p,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .forEach((code, pair) -> scores.add(
                        ReportGenerationOutput.SkillDomainScore.builder()
                                .domainCode(code)
                                .domainName(pair.getDomainName() != null ? pair.getDomainName() : code)
                                .score(BigDecimal.valueOf(75.0))
                                .achievedDepth(pair.getTargetDepth() != null ? pair.getTargetDepth() : "L3")
                                .commentary("候选人对该知识域有基本掌握，核心概念理解正确，建议进一步加深实践深度。")
                                .build()));
        return scores;
    }
}
