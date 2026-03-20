package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 客户端 Mock 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.openai.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final PromptProperties promptProperties;

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
        return mockResult(output, startMs, "planner");
    }

    @Override
    public Flux<String> callQuestionGenerationStream(QuestionGenerationInput input) {
        String domainCode = input.getNextQuestionGoal() != null
                ? input.getNextQuestionGoal().getNextDomainCode()
                : input.getNextDomainCode();
        String questionType = input.getNextQuestionGoal() != null
                ? input.getNextQuestionGoal().getQuestionType()
                : input.getNextQuestionType();
        String domainName = input.getNextQuestionGoal() != null
                ? input.getNextQuestionGoal().getNextDomainName()
                : input.getNextDomainName();
        log.info("[MockAI] callQuestionGenerationStream, decision={}, domainCode={}",
                input.getNextQuestionGoal() != null ? input.getNextQuestionGoal().getDecision() : null,
                domainCode);
        String stem = buildMockStem(domainName, questionType);
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

    @Override
    public AiCallResult<EvaluationDecisionOutput> callEvaluationDecision(EvaluationDecisionInput input) {
        log.info("[MockAI] callEvaluationDecision, domainCode={}, questionType={}, targetDepth={}",
                input.getCurrentDomainCode(), input.getCurrentQuestionType(), input.getCurrentTargetDepth());
        long startMs = System.currentTimeMillis();

        AnswerAssessment assessment = assessAnswer(input.getAnswerText());
        boolean shouldFollowupInDomain = assessment == AnswerAssessment.PASS && shouldDeepen(input);
        String decision = resolveDecision(input, assessment, shouldFollowupInDomain);
        EvaluationDecisionOutput.NextQuestionStrategy nextPlan = "wrapup".equals(decision)
                ? null
                : buildNextPlan(input, decision, shouldFollowupInDomain);

        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .answerAssessment(buildAssessmentText(assessment, input.getCurrentDomainName()))
                .answerVerdict(mapVerdict(assessment))
                .decision(decision)
                .targetFocus(resolveFocus(nextPlan, input))
                .targetAngle("implementation")
                .difficultyAdjustment(resolveDifficultyAdjustment(input, nextPlan))
                .nextQuestionGoal(buildNextQuestionGoal(decision, nextPlan, input))
                .nextDomainId(nextPlan != null ? nextPlan.getNextDomainId() : null)
                .nextDomainCode(nextPlan != null ? nextPlan.getNextDomainCode() : null)
                .nextDomainName(nextPlan != null ? nextPlan.getNextDomainName() : null)
                .questionType(nextPlan != null ? nextPlan.getQuestionType() : null)
                .focusPoint(nextPlan != null ? nextPlan.getFocusPoint() : null)
                .domainOutcome(resolveDomainOutcome(decision, assessment))
                .retrievalIntent(buildRetrievalIntent(input, nextPlan))
                .tags(buildTags(decision, nextPlan))
                .reasoning(shouldFollowupInDomain
                        ? "[Mock] 当前层通过，继续同域加深。"
                        : "[Mock] 当前层判断已完成，给出下一步策略。")
                .build();
        return mockResult(output, startMs, "evaluation_decision");
    }

    private String buildAssessmentText(AnswerAssessment assessment, String domainName) {
        return switch (assessment) {
            case PASS -> "候选人对" + domainName + "回答较完整，可以继续验证更有区分度的点。";
            case PARTIAL -> "候选人掌握了" + domainName + "的部分内容，但关键细节还不够扎实。";
            case HARD_FAIL -> "候选人在" + domainName + "上回答明显不足，继续硬压收益较低。";
        };
    }

    private String mapVerdict(AnswerAssessment assessment) {
        return switch (assessment) {
            case PASS -> "STRONG";
            case PARTIAL -> "PARTIAL";
            case HARD_FAIL -> "WEAK";
        };
    }

    private String resolveDomainOutcome(String decision, AnswerAssessment assessment) {
        if ("wrapup".equals(decision)) {
            return "covered";
        }
        if ("broaden".equals(decision) && assessment == AnswerAssessment.HARD_FAIL) {
            return "circuit_broken";
        }
        return "continue";
    }

    private String resolveFocus(EvaluationDecisionOutput.NextQuestionStrategy strategy, EvaluationDecisionInput input) {
        if (strategy != null && strategy.getFocusPoint() != null && !strategy.getFocusPoint().isBlank()) {
            return strategy.getFocusPoint();
        }
        return input.getCurrentDomainName();
    }

    private String resolveDifficultyAdjustment(EvaluationDecisionInput input,
                                               EvaluationDecisionOutput.NextQuestionStrategy strategy) {
        if (strategy == null) {
            return "same";
        }
        int current = depthIndex(input.getCurrentTargetDepth());
        int next = depthIndex(strategy.getTargetDepth());
        if (next > current) {
            return "up";
        }
        if (next < current) {
            return "down";
        }
        return "same";
    }

    private String buildNextQuestionGoal(String decision,
                                         EvaluationDecisionOutput.NextQuestionStrategy strategy,
                                         EvaluationDecisionInput input) {
        String focus = resolveFocus(strategy, input);
        return switch (decision) {
            case "followup" -> "继续顺着当前回答深挖" + focus;
            case "rescue" -> "降阶补问并验证" + focus + "的基础理解";
            case "wrapup" -> "结束当前面试";
            default -> "切到下一个关键域验证" + focus;
        };
    }

    private EvaluationDecisionOutput.RetrievalIntent buildRetrievalIntent(
            EvaluationDecisionInput input,
            EvaluationDecisionOutput.NextQuestionStrategy nextStrategy) {
        if (nextStrategy == null) {
            return null;
        }
        String focus = resolveFocus(nextStrategy, input);
        return EvaluationDecisionOutput.RetrievalIntent.builder()
                .domainHint(nextStrategy.getNextDomainCode())
                .focusQuery(focus + " " + nextStrategy.getQuestionType())
                .questionTypeHint(nextStrategy.getQuestionType())
                .avoidRecentFamilies(List.of())
                .build();
    }

    private EvaluationDecisionOutput.Tags buildTags(String decision,
                                                    EvaluationDecisionOutput.NextQuestionStrategy nextStrategy) {
        String intent = decision;
        String focus = nextStrategy != null ? nextStrategy.getFocusPoint() : "wrapup";
        return EvaluationDecisionOutput.Tags.builder()
                .questionFamilyHint(familyKey(nextStrategy, focus))
                .interviewerIntent(intent)
                .build();
    }

    private String familyKey(EvaluationDecisionOutput.NextQuestionStrategy nextStrategy, String focus) {
        if (nextStrategy == null) {
            return "interview.wrapup";
        }
        String domain = nextStrategy.getNextDomainCode() != null ? nextStrategy.getNextDomainCode() : "unknown";
        String angle = nextStrategy.getQuestionType() != null ? nextStrategy.getQuestionType().toLowerCase() : "generic";
        String normalizedFocus = focus == null ? "focus" : focus.replaceAll("\\s+", "-");
        return domain + "." + angle + "." + normalizedFocus;
    }

    @Override
    public AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input) {
        log.info("[MockAI] callReportGeneration, positionCode={}, qaCount={}",
                input.getPositionCode(),
                input.getQuestionAnswerPairs() != null ? input.getQuestionAnswerPairs().size() : 0);
        long startMs = System.currentTimeMillis();

        List<ReportGenerationOutput.SkillDomainScore> domainScores = buildMockDomainScores(input);
        List<ReportGenerationOutput.ComprehensiveRadarScore> radarScores =
                buildMockRadarScores(input.getMode());
        BigDecimal overallScore = domainScores.isEmpty()
                ? BigDecimal.valueOf(72.0)
                : domainScores.stream()
                        .map(ReportGenerationOutput.SkillDomainScore::getScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(domainScores.size()), 1, java.math.RoundingMode.HALF_UP);

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
                .comprehensiveRadarScores(radarScores)
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

    private EvaluationDecisionOutput.NextQuestionStrategy buildNextPlan(EvaluationDecisionInput input,
                                                                        String decision,
                                                                        boolean shouldFollowupInDomain) {
        String currentDomainCode = input.getCurrentDomainCode() != null ? input.getCurrentDomainCode() : "intro";
        String currentDomainName = input.getCurrentDomainName() != null ? input.getCurrentDomainName() : currentDomainCode;
        if ("rescue".equals(decision)) {
            String currentDepth = normalizeDepth(input.getCurrentTargetDepth());
            return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                    .nextDomainId(input.getCurrentDomainId())
                    .nextDomainCode(currentDomainCode)
                    .nextDomainName(currentDomainName)
                    .questionType(input.getCurrentQuestionType() != null ? input.getCurrentQuestionType() : "PRINCIPLE")
                    .targetDepth(currentDepth)
                    .difficulty(currentDepth)
                    .targetSkill(currentDomainName)
                    .expectedPoints(List.of(
                            "说明" + currentDomainName + "的核心概念",
                            "结合场景说明" + currentDomainName + "的基本用法"))
                    .focusPoint(currentDomainName)
                    .build();
        }
        if (shouldFollowupInDomain) {
            String currentDepth = normalizeDepth(input.getCurrentTargetDepth());
            String targetDepth = nextDepth(currentDepth, resolveDomainTargetDepth(input.getSyllabusJson(), currentDomainCode, currentDepth));
            return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                    .nextDomainId(input.getCurrentDomainId())
                    .nextDomainCode(currentDomainCode)
                    .nextDomainName(currentDomainName)
                    .questionType("PRINCIPLE")
                    .targetDepth(targetDepth)
                    .difficulty(targetDepth)
                    .targetSkill(currentDomainName + " 深度追问")
                    .expectedPoints(List.of(
                            "说明" + currentDomainName + "的关键机制",
                            "比较" + currentDomainName + "的常见取舍",
                            "结合场景说明" + currentDomainName + "的排查思路"))
                    .focusPoint(currentDomainName)
                    .build();
        }

        String nextDomainCode = resolveNextDomainCode(input.getStateLedger(), currentDomainCode);
        if (nextDomainCode == null) {
            nextDomainCode = currentDomainCode;
        }
        return buildStrategyFromSyllabus(input.getSyllabusJson(), nextDomainCode);
    }

    private String resolveDecision(EvaluationDecisionInput input,
                                   AnswerAssessment assessment,
                                   boolean shouldFollowupInDomain) {
        if (assessment == AnswerAssessment.PARTIAL) {
            return "rescue";
        }
        if (assessment == AnswerAssessment.HARD_FAIL) {
            String nextDomainCode = resolveNextDomainCode(input.getStateLedger(), input.getCurrentDomainCode());
            return nextDomainCode == null ? "wrapup" : "broaden";
        }
        if (shouldFollowupInDomain) {
            return "followup";
        }
        String nextDomainCode = resolveNextDomainCode(input.getStateLedger(), input.getCurrentDomainCode());
        return nextDomainCode == null ? "wrapup" : "broaden";
    }

    private String resolveNextDomainCode(Map<String, Object> ledger, String currentDomainCode) {
        if (ledger == null) {
            return null;
        }
        Object domainStatesObj = ledger.get("domain_states");
        if (!(domainStatesObj instanceof List<?> states)) {
            return null;
        }
        for (Object item : states) {
            if (!(item instanceof Map<?, ?> state)) {
                continue;
            }
            String code = Objects.toString(state.get("domain_id"), "");
            String status = Objects.toString(state.get("status"), "");
            if (code.isBlank() || Objects.equals(code, currentDomainCode)) {
                continue;
            }
            if (status.isBlank() || "UNASKED".equalsIgnoreCase(status)) {
                return code;
            }
        }
        return null;
    }

    private EvaluationDecisionOutput.NextQuestionStrategy buildStrategyFromSyllabus(Map<String, Object> syllabus,
                                                                                    String domainCode) {
        Long nextDomainId = null;
        String nextDomainName = domainCode;
        String targetDepth = "L2";
        String focusPoint = domainCode;

        if (syllabus != null && syllabus.get("domains") instanceof List<?> domains) {
            for (Object d : domains) {
                if (!(d instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> domain = new LinkedHashMap<>();
                raw.forEach((k, v) -> domain.put(String.valueOf(k), v));
                if (!Objects.equals(domainCode, domain.get("domainCode"))) {
                    continue;
                }
                Object idObj = domain.get("domainId");
                if (idObj instanceof Number num) {
                    nextDomainId = num.longValue();
                }
                if (domain.get("domainName") instanceof String name && !name.isBlank()) {
                    nextDomainName = name;
                }
                if (domain.get("targetDepth") instanceof String td && !td.isBlank()) {
                    targetDepth = normalizeDepth(td);
                }
                if (domain.get("focusPoints") instanceof List<?> focusPoints) {
                    for (Object focus : focusPoints) {
                        if (focus instanceof String fp && !fp.isBlank()) {
                            focusPoint = fp;
                            break;
                        }
                    }
                }
                break;
            }
        }

        return EvaluationDecisionOutput.NextQuestionStrategy.builder()
                .nextDomainId(nextDomainId)
                .nextDomainCode(domainCode)
                .nextDomainName(nextDomainName)
                .questionType("PRINCIPLE")
                .targetDepth(targetDepth)
                .difficulty(targetDepth)
                .targetSkill(focusPoint)
                .expectedPoints(List.of(
                        "说明" + focusPoint + "的核心概念",
                        "比较" + focusPoint + "的常见方案",
                        "结合场景说明" + focusPoint + "的选择依据"))
                .focusPoint(focusPoint)
                .build();
    }

    private boolean shouldDeepen(EvaluationDecisionInput input) {
        String currentDomainCode = input.getCurrentDomainCode() != null ? input.getCurrentDomainCode() : "intro";
        String currentTargetDepth = normalizeDepth(input.getCurrentTargetDepth());
        String domainTargetDepth = resolveDomainTargetDepth(input.getSyllabusJson(), currentDomainCode, currentTargetDepth);
        return depthIndex(currentTargetDepth) < depthIndex(domainTargetDepth)
                && !"INTRO".equalsIgnoreCase(input.getCurrentQuestionType());
    }

    private String resolveDomainTargetDepth(Map<String, Object> syllabus, String domainCode, String fallbackDepth) {
        if (syllabus != null && syllabus.get("domains") instanceof List<?> domains) {
            for (Object d : domains) {
                if (!(d instanceof Map<?, ?> dm)) {
                    continue;
                }
                if (!Objects.equals(domainCode, dm.get("domainCode"))) {
                    continue;
                }
                String targetDepth = Objects.toString(dm.get("targetDepth"), "");
                if (!targetDepth.isBlank()) {
                    return normalizeDepth(targetDepth);
                }
            }
        }
        return normalizeDepth(fallbackDepth);
    }

    private AnswerAssessment assessAnswer(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return AnswerAssessment.HARD_FAIL;
        }
        String normalized = answerText.toLowerCase();
        if (normalized.contains("不会")
                || normalized.contains("不熟悉")
                || normalized.contains("不知道")
                || normalized.contains("不了解")) {
            return AnswerAssessment.HARD_FAIL;
        }
        if (normalized.contains("有点忘了")
                || normalized.contains("记不清")
                || normalized.contains("大概")
                || normalized.contains("部分")) {
            return AnswerAssessment.PARTIAL;
        }
        return AnswerAssessment.PASS;
    }

    private String normalizeDepth(String depth) {
        if (depth == null || depth.isBlank()) {
            return "L2";
        }
        String normalized = depth.trim().toUpperCase();
        if (normalized.matches("L[1-5]")) {
            return normalized;
        }
        return "L2";
    }

    private int depthIndex(String depth) {
        return normalizeDepth(depth).charAt(1) - '0';
    }

    private String nextDepth(String currentDepth, String targetDepth) {
        int next = Math.min(depthIndex(currentDepth) + 1, depthIndex(targetDepth));
        return "L" + next;
    }

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

    private List<ReportGenerationOutput.SkillDomainScore> buildMockDomainScores(ReportGenerationInput input) {
        if (input.getQuestionAnswerPairs() == null) {
            return List.of();
        }
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

    private List<ReportGenerationOutput.ComprehensiveRadarScore> buildMockRadarScores(String mode) {
        if (!"professional".equalsIgnoreCase(mode)) {
            return null;
        }
        return List.of(
                new ReportGenerationOutput.ComprehensiveRadarScore("fundamentals", "基础原理掌握", BigDecimal.valueOf(84)),
                new ReportGenerationOutput.ComprehensiveRadarScore("engineering_practice", "工程实践与项目落地", BigDecimal.valueOf(78)),
                new ReportGenerationOutput.ComprehensiveRadarScore("scenario_tradeoff", "场景分析与方案取舍", BigDecimal.valueOf(72)),
                new ReportGenerationOutput.ComprehensiveRadarScore("debugging", "问题定位与排查思路", BigDecimal.valueOf(76)),
                new ReportGenerationOutput.ComprehensiveRadarScore("communication", "沟通表达与结构化呈现", BigDecimal.valueOf(81))
        );
    }

    private enum AnswerAssessment {
        PASS,
        PARTIAL,
        HARD_FAIL
    }
}
