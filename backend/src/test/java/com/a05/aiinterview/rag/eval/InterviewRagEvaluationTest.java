package com.a05.aiinterview.rag.eval;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagPlanCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Interview RAG evaluation harness")
class InterviewRagEvaluationTest {

    /**
     * Fixed evaluation budgets for the Task 1 harness.
     * Compile budget: < 10 ms
     * Single retrieval p95: < 300 ms
     * End-to-end extra latency budget: < 500 ms
     */
    private static final double DENSE_RECALL_HIT_RATE_GATE = 0.6d;
    private static final long RETRIEVAL_COMPILE_BUDGET_MS = 10L;
    private static final long SINGLE_RETRIEVAL_P95_BUDGET_MS = 300L;
    private static final long END_TO_END_EXTRA_LATENCY_BUDGET_MS = 500L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RagPlanCompiler compiler = new RagPlanCompiler();

    @Test
    @DisplayName("fixture should contain the required sample skeleton")
    void fixture_shouldContainRequiredSampleSkeleton() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();

        assertThat(fixture.schemaVersion()).isEqualTo(1);
        assertThat(fixture.cases()).hasSize(12);
        assertThat(fixture.cases()).anyMatch(sample -> !sample.shouldRetrieve());
        assertThat(fixture.cases()).anyMatch(sample -> "PROJECT".equals(sample.questionType()));

        for (InterviewRetrievalCase sample : fixture.cases()) {
            assertThat(sample.traceId()).isNotBlank();
            assertThat(sample.questionType()).isNotBlank();
            assertThat(sample.focusPoint()).isNotBlank();
            assertThat(sample.mustHaveClues()).isNotNull();
            assertThat(sample.avoidClues()).isNotNull();

            if (sample.shouldRetrieve()) {
                RetrievalPlan retrievalPlan = requireSingleRetrievalPlan(sample);
                assertThat(sample.expectedKeywords()).isNotEmpty();
                assertThat(retrievalPlan.keywordHints()).containsAll(sample.expectedKeywords());
                assertThat(retrievalPlan.mustHaveClues()).containsExactlyElementsOf(sample.mustHaveClues());
                assertThat(retrievalPlan.avoidClues()).containsExactlyElementsOf(sample.avoidClues());
            } else {
                assertThat(sample.retrievalPlans()).isEmpty();
                assertThat(sample.expectedKeywords()).isEmpty();
                assertThat(sample.expectedFollowUpIds()).isEmpty();
                assertThat(sample.mustHaveClues()).isEmpty();
                assertThat(sample.avoidClues()).isEmpty();
            }
        }

        assertThat(RETRIEVAL_COMPILE_BUDGET_MS).as("fixed compile budget, in ms").isEqualTo(10L);
        assertThat(SINGLE_RETRIEVAL_P95_BUDGET_MS).as("fixed single retrieval p95 budget, in ms").isEqualTo(300L);
        assertThat(END_TO_END_EXTRA_LATENCY_BUDGET_MS).as("fixed end-to-end extra latency budget, in ms").isEqualTo(500L);
    }

    @Test
    @DisplayName("metric gate should keep failing until denseRecallHitRate reaches 0.6")
    void metricGate_shouldKeepFailingUntilDenseRecallReachesThreshold() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();
        // Task 1 placeholder: stage hits are not wired to real retrieval yet.
        List<SampleEvaluationResult> sampleResults = fixture.cases().stream()
                .map(this::evaluateSample)
                .toList();
        EvaluationReport report = aggregate(sampleResults);

        assertThat(report.routingAccuracy()).isEqualTo(1.0d);
        assertThat(report.routeMismatchTraceIds()).isEmpty();
        assertThat(report.retrievalApplicableHitRate()).isEqualTo(0.0d);
        assertThat(report.mustHaveCoverage()).isEqualTo(0.0d);
        assertThat(report.avoidPollutionRate()).isEqualTo(0.0d);
        assertThat(report.denseRecallHitRate())
                .withFailMessage(report.failureSummary(DENSE_RECALL_HIT_RATE_GATE))
                .isGreaterThanOrEqualTo(DENSE_RECALL_HIT_RATE_GATE);
    }

    @Test
    @DisplayName("compiled routing should match fixture policy and preserve expected keywords")
    void compiledRouting_shouldMatchFixturePolicyAndPreserveExpectedKeywords() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();

        for (InterviewRetrievalCase sample : fixture.cases()) {
            RagRetrievalRequest request = compile(sample);
            assertThat(request.isShouldRetrieve())
                    .as("traceId=%s route mismatch", sample.traceId())
                    .isEqualTo(sample.shouldRetrieve());

            if (sample.shouldRetrieve()) {
                assertThat(request.getKeywordQueries())
                        .as("traceId=%s keyword coverage", sample.traceId())
                        .containsAll(sample.expectedKeywords());
            } else {
                assertThat(request.getKeywordQueries()).isEmpty();
                assertThat(request.getQueryText()).isBlank();
            }
        }
    }

    private SampleEvaluationResult evaluateSample(InterviewRetrievalCase sample) {
        RagRetrievalRequest compiled = compile(sample);
        boolean actualShouldRetrieve = compiled.isShouldRetrieve();
        boolean routingMatched = actualShouldRetrieve == sample.shouldRetrieve();
        // Pre-integration placeholder: no real retrieval is executed in Task 1.
        StageResult stageResult = new StageResult(
                false,
                false,
                false,
                false
        );
        boolean retrieved = stageResult.hasAnyHit();
        boolean mustHaveCovered = retrieved && matchesMustHaveClues(sample);
        boolean avoidPolluted = retrieved && false;

        return new SampleEvaluationResult(
                sample.traceId(),
                actualShouldRetrieve,
                routingMatched,
                stageResult,
                retrieved,
                mustHaveCovered,
                avoidPolluted
        );
    }

    /**
     * Task 1 fixture contract: every retrievable case must contain exactly one retrieval plan.
     */
    private RetrievalPlan requireSingleRetrievalPlan(InterviewRetrievalCase sample) {
        assertThat(sample.retrievalPlans())
                .as("traceId=%s should contain exactly one retrieval plan", sample.traceId())
                .hasSize(1);
        return sample.retrievalPlans().get(0);
    }

    private RagRetrievalRequest compile(InterviewRetrievalCase sample) {
        DecisionExecutionPlan plan = DecisionExecutionPlan.builder()
                .targetQuestionType(normalizeQuestionType(sample.questionType()))
                .nextFocus(sample.focusPoint())
                .nextItemName("PROJECT".equals(normalize(sample.questionType())) ? "样例项目" : "")
                .retrievalPlans(toOutputPlans(sample.retrievalPlans()))
                .build();
        return compiler.compile(plan, "JAVA_BACKEND", "FRESH_GRAD");
    }

    private List<EvaluationDecisionOutput.RetrievalPlan> toOutputPlans(List<RetrievalPlan> retrievalPlans) {
        if (retrievalPlans == null) {
            return List.of();
        }
        return retrievalPlans.stream()
                .map(plan -> EvaluationDecisionOutput.RetrievalPlan.builder()
                        .goal(plan.goal())
                        .displayQuery(plan.displayQuery())
                        .queryText(plan.queryText())
                        .keywordHints(plan.keywordHints())
                        .difficultyHint(plan.difficultyHint())
                        .mustHaveClues(plan.mustHaveClues())
                        .avoidClues(plan.avoidClues())
                        .build())
                .toList();
    }

    private boolean matchesMustHaveClues(InterviewRetrievalCase sample) {
        return requireSingleRetrievalPlan(sample).mustHaveClues().equals(sample.mustHaveClues());
    }

    private EvaluationReport aggregate(List<SampleEvaluationResult> sampleResults) {
        int total = sampleResults.size();
        List<String> routeMismatchTraceIds = new ArrayList<>();
        List<String> denseRecallMissTraceIds = new ArrayList<>();

        int routingMatches = 0;
        int retrievableSamples = 0;
        int retrievedSamples = 0;
        int mustHaveCoverageHits = 0;
        int avoidPollutionHits = 0;
        int denseRecallHits = 0;
        int sparseRecallHits = 0;
        int fusionHits = 0;
        int rerankHits = 0;

        for (SampleEvaluationResult result : sampleResults) {
            if (result.routingMatched()) {
                routingMatches++;
            } else {
                routeMismatchTraceIds.add(result.traceId());
            }

            if (result.actualShouldRetrieve()) {
                retrievableSamples++;
                if (result.retrieved()) {
                    retrievedSamples++;
                }
                if (result.mustHaveCovered()) {
                    mustHaveCoverageHits++;
                }
                if (result.avoidPolluted()) {
                    avoidPollutionHits++;
                }
                if (result.stageResult().denseRecallHit()) {
                    denseRecallHits++;
                } else {
                    denseRecallMissTraceIds.add(result.traceId());
                }
                if (result.stageResult().sparseRecallHit()) {
                    sparseRecallHits++;
                }
                if (result.stageResult().fusionHit()) {
                    fusionHits++;
                }
                if (result.stageResult().rerankHit()) {
                    rerankHits++;
                }
            }
        }

        double routingAccuracy = total == 0 ? 1.0d : (double) routingMatches / total;
        double retrievalApplicableHitRate = retrievableSamples == 0 ? 1.0d : (double) retrievedSamples / retrievableSamples;
        double mustHaveCoverage = retrievableSamples == 0 ? 1.0d : (double) mustHaveCoverageHits / retrievableSamples;
        double avoidPollutionRate = retrievableSamples == 0 ? 0.0d : (double) avoidPollutionHits / retrievableSamples;
        double denseRecallHitRate = retrievableSamples == 0 ? 0.0d : (double) denseRecallHits / retrievableSamples;
        double sparseRecallHitRate = retrievableSamples == 0 ? 0.0d : (double) sparseRecallHits / retrievableSamples;
        double fusionLift = retrievableSamples == 0 ? 0.0d : (double) fusionHits / retrievableSamples;
        double rerankTop3HitRate = retrievableSamples == 0 ? 0.0d : (double) rerankHits / retrievableSamples;

        return new EvaluationReport(
                total,
                routeMismatchTraceIds,
                denseRecallMissTraceIds,
                routingAccuracy,
                retrievalApplicableHitRate,
                mustHaveCoverage,
                avoidPollutionRate,
                denseRecallHitRate,
                sparseRecallHitRate,
                fusionLift,
                rerankTop3HitRate
        );
    }

    private InterviewRetrievalFixture loadFixture() throws Exception {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("rag-eval/interview-retrieval-cases.json")) {
            InputStream stream = Objects.requireNonNull(inputStream, "Missing rag-eval/interview-retrieval-cases.json");
            return MAPPER.readValue(stream, InterviewRetrievalFixture.class);
        }
    }

    private record InterviewRetrievalFixture(int schemaVersion, List<InterviewRetrievalCase> cases) {
    }

    private record InterviewRetrievalCase(
            String traceId,
            String questionType,
            String focusPoint,
            boolean shouldRetrieve,
            String difficultyHint,
            List<String> expectedKeywords,
            List<String> mustHaveClues,
            List<String> avoidClues,
            List<String> expectedFollowUpIds,
            List<RetrievalPlan> retrievalPlans
    ) {
    }

    private record RetrievalPlan(
            String goal,
            String displayQuery,
            String queryText,
            List<String> keywordHints,
            String difficultyHint,
            List<String> mustHaveClues,
            List<String> avoidClues
    ) {
    }

    private record EvaluationReport(
            int totalSamples,
            List<String> routeMismatchTraceIds,
            List<String> denseRecallMissTraceIds,
            double routingAccuracy,
            double retrievalApplicableHitRate,
            double mustHaveCoverage,
            double avoidPollutionRate,
            double denseRecallHitRate,
            double sparseRecallHitRate,
            double fusionLift,
            double rerankTop3HitRate
    ) {
        private String failureSummary(double gate) {
            return """
                    Interview RAG evaluation gate failed
                    stageModel=placeholder_pre_integration
                    totalSamples=%d
                    routeMismatchTraceIds=%s
                    denseRecallMissTraceIds=%s
                    metrics={routingAccuracy=%.3f, retrievalApplicableHitRate=%.3f, mustHaveCoverage=%.3f, avoidPollutionRate=%.3f, denseRecallHitRate=%.3f, sparseRecallHitRate=%.3f, fusionLift=%.3f, rerankTop3HitRate=%.3f}
                    gate=denseRecallHitRate>=%.1f
                    """.formatted(
                    totalSamples,
                    routeMismatchTraceIds,
                    denseRecallMissTraceIds,
                    routingAccuracy,
                    retrievalApplicableHitRate,
                    mustHaveCoverage,
                    avoidPollutionRate,
                    denseRecallHitRate,
                    sparseRecallHitRate,
                    fusionLift,
                    rerankTop3HitRate,
                    gate
            );
        }
    }

    private record SampleEvaluationResult(
            String traceId,
            boolean actualShouldRetrieve,
            boolean routingMatched,
            StageResult stageResult,
            boolean retrieved,
            boolean mustHaveCovered,
            boolean avoidPolluted
    ) {
    }

    private record StageResult(
            boolean denseRecallHit,
            boolean sparseRecallHit,
            boolean fusionHit,
            boolean rerankHit
    ) {
        private boolean hasAnyHit() {
            return denseRecallHit || sparseRecallHit || fusionHit || rerankHit;
        }
    }

    private String normalizeQuestionType(String questionType) {
        String normalized = normalize(questionType);
        return "PROJECT".equals(normalized) ? "PROJECT_DEEP_DIVE" : normalized;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase();
    }
}
