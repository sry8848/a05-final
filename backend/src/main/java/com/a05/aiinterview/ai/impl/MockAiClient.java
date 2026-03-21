package com.a05.aiinterview.ai.impl;

import com.a05.aiinterview.ai.AiClient;
import com.a05.aiinterview.ai.config.PromptProperties;
import com.a05.aiinterview.ai.dto.AiCallResult;
import com.a05.aiinterview.ai.dto.EvaluationDecisionInput;
import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.ai.dto.IntroRewriteInput;
import com.a05.aiinterview.ai.dto.PlannerInput;
import com.a05.aiinterview.ai.dto.PlannerOutput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationInput;
import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;
import com.a05.aiinterview.ai.dto.QuestionGenerationInput;
import com.a05.aiinterview.ai.dto.ReportGenerationInput;
import com.a05.aiinterview.ai.dto.ReportGenerationOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 客户端 Mock 实现。
 * 仅用于本地未接入真实 AI 时的最小可运行兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.openai.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockAiClient implements AiClient {

    private final PromptProperties promptProperties;

    @Override
    public AiCallResult<PlannerOutput> callPlanner(PlannerInput input) {
        long startMs = System.currentTimeMillis();
        PlannerOutput output = PlannerOutput.builder()
                .planningReasoning("根据岗位要求、简历项目与候选人背景生成基础考纲。")
                .domains(input.getDomains() == null ? List.of() : input.getDomains().stream()
                        .limit(5)
                        .map(domain -> PlannerOutput.DomainPlan.builder()
                                .domainCode(domain.getDomainCode())
                                .domainName(domain.getDomainName())
                                .focusPoints(List.of())
                                .build())
                        .toList())
                .experienceItems(List.of())
                .build();
        return mockResult(output, startMs, "planner");
    }

    @Override
    public Flux<String> callQuestionGenerationStream(QuestionGenerationInput input) {
        String focus = input.getNextQuestionGoal() != null ? input.getNextQuestionGoal().getNextFocus() : "当前主题";
        String questionType = input.getNextQuestionGoal() != null ? input.getNextQuestionGoal().getQuestionType() : "PRINCIPLE";
        String stem = switch ((questionType == null ? "" : questionType).toUpperCase()) {
            case "PROJECT_DEEP_DIVE" -> "结合你做过的真实项目，详细讲讲「" + focus + "」这块你当时是怎么设计和落地的？";
            case "SCENARIO" -> "如果线上在「" + focus + "」这里出现异常，你会怎么判断、排查和处理？";
            case "BEHAVIORAL" -> "请分享一次你围绕「" + focus + "」推进协作或解决分歧的真实经历。";
            default -> "请你系统讲讲「" + focus + "」的原理、常见方案和使用边界。";
        };
        return Flux.fromArray(stem.split("")).delayElements(Duration.ofMillis(20));
    }

    @Override
    public AiCallResult<String> callIntroRewrite(IntroRewriteInput input) {
        long startMs = System.currentTimeMillis();
        String rewritten = input.getBasePrompt() == null || input.getBasePrompt().isBlank()
                ? "请先做一个简短的自我介绍，重点讲讲你的技术背景和最近的项目经历。"
                : input.getBasePrompt().trim();
        return mockResult(rewritten, startMs, "intro_rewrite");
    }

    @Override
    public AiCallResult<EvaluationDecisionOutput> callEvaluationDecision(EvaluationDecisionInput input) {
        long startMs = System.currentTimeMillis();
        String nextFocus = input.getCurrentQuestion() != null ? input.getCurrentQuestion().getCurrentFocus() : "当前主题";
        EvaluationDecisionOutput output = EvaluationDecisionOutput.builder()
                .interviewAction("CONTINUE")
                .answerSummary("候选人完成了当前问题的基本回答。")
                .answerAssessment("候选人有一定理解，但仍可通过下一题继续建立画像。")
                .decisionReason("当前继续提问仍有信息增益，因此保持面试继续推进。")
                .candidateStrategies(List.of("继续提问"))
                .finalDecision("继续提问")
                .nextQuestionType("PRINCIPLE")
                .nextFocus(nextFocus)
                .expectedAnswerPoints(List.of("说明核心概念", "解释使用边界"))
                .newCoveredDomains(List.of())
                .newCoveredPoints(List.of())
                .retrievalPlans(List.of())
                .build();
        return mockResult(output, startMs, "evaluation_decision");
    }

    @Override
    public AiCallResult<ReportGenerationOutput> callReportGeneration(ReportGenerationInput input) {
        long startMs = System.currentTimeMillis();
        List<ReportGenerationOutput.SkillDomainScore> domainScores = buildMockDomainScores(input);
        ReportGenerationOutput output = ReportGenerationOutput.builder()
                .overallScore(BigDecimal.valueOf(75.0))
                .summary("候选人整体表现稳定，基础知识和工程表达具备一定水准。")
                .strengths(List.of("表达清晰", "基础知识较完整", "有一定工程经验"))
                .weaknesses(List.of("复杂场景下的取舍不够深入", "部分回答仍偏概念化"))
                .improvementSuggestions(List.of("补齐高并发和分布式场景经验", "加强问题排查与方案权衡训练"))
                .comprehensiveRadarScores(null)
                .skillDomainScores(domainScores)
                .build();
        return mockResult(output, startMs, "report_generation");
    }

    @Override
    public AiCallResult<QuestionDetailEvaluationOutput> callQuestionDetailEvaluation(QuestionDetailEvaluationInput input) {
        long startMs = System.currentTimeMillis();
        QuestionDetailEvaluationOutput output = QuestionDetailEvaluationOutput.builder()
                .score(BigDecimal.valueOf(75.0))
                .commentary("回答覆盖了部分关键点，但还可以进一步补强细节和边界。")
                .strengthPoints(List.of("主线表达清晰"))
                .weakPoints(List.of("关键机制解释不够充分"))
                .evaluatedDomains(List.of(QuestionDetailEvaluationOutput.EvaluatedDomain.builder()
                        .domainCode(input.getDomainCode())
                        .domainName(input.getDomainName())
                        .score(BigDecimal.valueOf(75.0))
                        .commentary("当前知识域掌握尚可。")
                        .build()))
                .highlightedSegments(List.of(QuestionDetailEvaluationOutput.HighlightedSegment.builder()
                        .segment(input.getAnswerText())
                        .label("strength")
                        .comment("主线表达完整，但还可补更多细节。")
                        .build()))
                .idealAnswerOutline(List.of("先说明核心原理", "再结合场景说明方案取舍"))
                .rewrittenAnswer(input.getAnswerText())
                .build();
        return mockResult(output, startMs, "question_detail_evaluation");
    }

    private List<ReportGenerationOutput.SkillDomainScore> buildMockDomainScores(ReportGenerationInput input) {
        if (input.getQuestionAnswerPairs() == null) {
            return List.of();
        }
        List<ReportGenerationOutput.SkillDomainScore> scores = new ArrayList<>();
        input.getQuestionAnswerPairs().stream()
                .filter(pair -> pair.getDomainCode() != null && !pair.getDomainCode().isBlank())
                .map(pair -> ReportGenerationOutput.SkillDomainScore.builder()
                        .domainCode(pair.getDomainCode())
                        .domainName(pair.getDomainName() != null ? pair.getDomainName() : pair.getDomainCode())
                        .score(BigDecimal.valueOf(75.0))
                        .commentary("候选人对该知识域有基本掌握。")
                        .build())
                .forEach(scores::add);
        return scores;
    }

    private <T> AiCallResult<T> mockResult(T output, long startMs, String promptCode) {
        return AiCallResult.<T>builder()
                .output(output)
                .promptCode(promptCode)
                .promptVersion(promptProperties.resolveVersion(promptCode))
                .promptTokens(0)
                .responseTokens(0)
                .latencyMs(System.currentTimeMillis() - startMs)
                .systemPrompt(null)
                .userPrompt(null)
                .rawResponse(null)
                .build();
    }
}
