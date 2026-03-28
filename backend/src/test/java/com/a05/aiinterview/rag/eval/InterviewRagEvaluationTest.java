package com.a05.aiinterview.rag.eval;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import com.a05.aiinterview.rag.service.RagPlanCompiler;
import com.a05.aiinterview.rag.service.impl.RagRetrievalServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    private static final String DEFAULT_OPENAI_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v4";

    private final RagPlanCompiler compiler = new RagPlanCompiler();

    @Test
    @DisplayName("fixture should contain the required sample skeleton")
    void fixture_shouldContainRequiredSampleSkeleton() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();

        assertThat(fixture.schemaVersion()).isEqualTo(1);
        assertThat(fixture.cases()).hasSizeGreaterThanOrEqualTo(14);
        assertThat(fixture.cases()).anyMatch(sample -> !sample.shouldRetrieve());
        assertThat(fixture.cases()).anyMatch(sample -> "PROJECT".equals(sample.questionType()));
        assertThat(fixture.cases()).anyMatch(sample -> "session-83_q-215_attempt-threadlocal-cross-thread".equals(sample.traceId()));
        assertThat(fixture.cases()).anyMatch(sample -> "session-84_q-218_attempt-generic-project-architecture".equals(sample.traceId()));

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
    @DisplayName("metric gate should pass once dense recall is wired to real hybrid retrieval")
    void metricGate_shouldPassOnceDenseRecallReachesThreshold() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();

        try (RealRetrievalHarness harness = RealRetrievalHarness.create(fixture.cases())) {
            List<SampleEvaluationResult> sampleResults = fixture.cases().stream()
                    .map(sample -> evaluateSample(sample, harness))
                    .toList();
            EvaluationReport report = aggregate(sampleResults);

            assertThat(report.routingAccuracy()).isEqualTo(1.0d);
            assertThat(report.routeMismatchTraceIds()).isEmpty();
            assertThat(report.retrievalApplicableHitRate()).isGreaterThanOrEqualTo(0.8d);
            assertThat(report.mustHaveCoverage()).isGreaterThanOrEqualTo(0.7d);
            assertThat(report.avoidPollutionRate()).isLessThanOrEqualTo(0.2d);
            assertThat(report.denseRecallHitRate())
                    .withFailMessage(report.failureSummary(DENSE_RECALL_HIT_RATE_GATE))
                    .isGreaterThanOrEqualTo(DENSE_RECALL_HIT_RATE_GATE);
        }
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

    @Test
    @DisplayName("spot checks should guard against the main retrieval regressions")
    void spotChecks_shouldGuardAgainstMainRetrievalRegressions() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();

        try (RealRetrievalHarness harness = RealRetrievalHarness.create(fixture.cases())) {
            InterviewRetrievalCase behavioral = findCase(fixture, "session-74_q-188_attempt-a8cc71d8-3f2b-4332-b8d0-4ec32f2f28e1");
            RagContext behavioralContext = harness.retrievalService.retrieve(compile(behavioral));
            assertThat(behavioralContext.getRetrievedMaterials()).isNotEmpty();
            assertThat(behavioralContext.getRetrievedMaterials().getFirst().getQuestionType()).isEqualTo("BEHAVIORAL");
            assertThat(behavioralContext.getRetrievedMaterials())
                    .allSatisfy(material -> {
                        assertThat(material.getDomainCode()).isBlank();
                        assertThat(material.getQuestionText()).doesNotContain("Redis", "Seata", "MySQL");
                    });

            InterviewRetrievalCase projectTechHook = findCase(fixture, "session-76_q-194_attempt-7c2d4e26-51e3-4c38-b5cd-3fa5d10c2d7d");
            RagContext projectContext = harness.retrievalService.retrieve(compile(projectTechHook));
            assertThat(projectContext.getRetrievedMaterials()).isNotEmpty();
            assertThat(projectContext.getRetrievedMaterials().getFirst().getQuestionType()).isEqualTo("PROJECT");
            assertThat(projectContext.getRetrievedMaterials().getFirst().getQuestionText()).contains("Seata", "XID");

            InterviewRetrievalCase genericProject = findCase(fixture, "session-84_q-218_attempt-generic-project-architecture");
            RagRetrievalRequest genericRequest = compile(genericProject);
            assertThat(genericRequest.isShouldRetrieve()).isFalse();

            InterviewRetrievalCase threadLocal = findCase(fixture, "session-83_q-215_attempt-threadlocal-cross-thread");
            RagContext threadLocalContext = harness.retrievalService.retrieve(compile(threadLocal));
            assertThat(threadLocalContext.getRetrievedMaterials()).isNotEmpty();
            assertThat(threadLocalContext.getRetrievedMaterials().getFirst().getQuestionId()).isEqualTo(threadLocal.traceId());
            assertThat(threadLocalContext.getFollowUpCandidates()).contains("threadlocal-transmittable-thread-local-001");
            assertThat(threadLocalContext.getFollowUpCandidates()).doesNotContain(threadLocal.traceId());
        }
    }

    private SampleEvaluationResult evaluateSample(InterviewRetrievalCase sample, RealRetrievalHarness harness) {
        RagRetrievalRequest compiled = compile(sample);
        boolean actualShouldRetrieve = compiled.isShouldRetrieve();
        boolean routingMatched = actualShouldRetrieve == sample.shouldRetrieve();
        if (!actualShouldRetrieve) {
            return new SampleEvaluationResult(
                    sample.traceId(),
                    false,
                    routingMatched,
                    new StageResult(false, false, false, false),
                    false,
                    false,
                    false
            );
        }

        StageResult stageResult = harness.evaluateStages(sample, compiled);
        RagContext context = harness.retrievalService.retrieve(compiled);
        boolean retrieved = containsQuestionId(context.getRetrievedMaterials(), sample.traceId());
        boolean mustHaveCovered = hasMustHaveCoverage(context, sample);
        boolean avoidPolluted = hasAvoidPollution(context, sample);

        return new SampleEvaluationResult(
                sample.traceId(),
                true,
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
        return sample.retrievalPlans().getFirst();
    }

    private RagRetrievalRequest compile(InterviewRetrievalCase sample) {
        DecisionExecutionPlan plan = DecisionExecutionPlan.builder()
                .targetQuestionType(normalizeQuestionType(sample.questionType()))
                .targetDomainCode(inferDomainCode(sample))
                .nextFocus(sample.focusPoint())
                .nextItemName("PROJECT".equals(normalize(sample.questionType())) ? "样例项目" : "")
                .retrievalPlans(toOutputPlans(sample.retrievalPlans()))
                .build();
        return compiler.compile(plan, "JAVA_BACKEND", "FRESH_GRAD");
    }

    private InterviewRetrievalCase findCase(InterviewRetrievalFixture fixture, String traceId) {
        return fixture.cases().stream()
                .filter(sample -> traceId.equals(sample.traceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing fixture case: " + traceId));
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

    private boolean containsQuestionId(List<RagContext.RetrievedMaterial> materials, String expectedQuestionId) {
        return materials.stream().anyMatch(material -> Objects.equals(expectedQuestionId, material.getQuestionId()));
    }

    private boolean hasMustHaveCoverage(RagContext context, InterviewRetrievalCase sample) {
        return context.getRetrievedMaterials().stream()
                .filter(material -> Objects.equals(sample.traceId(), material.getQuestionId()))
                .findFirst()
                .map(material -> material.getScoringKeyPoints().containsAll(sample.mustHaveClues()))
                .orElse(false);
    }

    private boolean hasAvoidPollution(RagContext context, InterviewRetrievalCase sample) {
        return context.getRetrievedMaterials().stream()
                .filter(material -> !Objects.equals(sample.traceId(), material.getQuestionId()))
                .anyMatch(material -> containsAnyClue(material.getReferenceContext(), sample.avoidClues())
                        || containsAnyClue(material.getIntentConcept(), sample.avoidClues())
                        || material.getScoringPitfalls().stream().anyMatch(pitfall -> containsAnyClue(pitfall, sample.avoidClues())));
    }

    private boolean containsAnyClue(String text, List<String> clues) {
        if (text == null || text.isBlank() || clues == null || clues.isEmpty()) {
            return false;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        return clues.stream()
                .filter(Objects::nonNull)
                .map(clue -> clue.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedText::contains);
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

    private String normalizeQuestionType(String questionType) {
        String normalized = normalize(questionType);
        return "PROJECT".equals(normalized) ? "PROJECT_DEEP_DIVE" : normalized;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private String inferDomainCode(InterviewRetrievalCase sample) {
        String focus = (sample.focusPoint() + " " + String.join(" ", sample.expectedKeywords())).toLowerCase(Locale.ROOT);
        if ("BEHAVIORAL".equals(normalize(sample.questionType()))) {
            return "";
        }
        if (focus.contains("redis") || focus.contains("redisson") || focus.contains("缓存")) {
            return "redis";
        }
        if (focus.contains("mysql") || focus.contains("innodb") || focus.contains("索引")) {
            return "mysql";
        }
        if (focus.contains("hashmap") || focus.contains("java")) {
            return "java_core";
        }
        if (focus.contains("mq") || focus.contains("seata") || focus.contains("xid") || focus.contains("订单")) {
            return "distributed";
        }
        return "";
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
    }

    private static final class RealRetrievalHarness implements AutoCloseable {
        private final QdrantClient qdrantClient;
        private final String collectionName;
        private final RagRetrievalServiceImpl retrievalService;

        private RealRetrievalHarness(QdrantClient qdrantClient, String collectionName, RagRetrievalServiceImpl retrievalService) {
            this.qdrantClient = qdrantClient;
            this.collectionName = collectionName;
            this.retrievalService = retrievalService;
        }

        private static RealRetrievalHarness create(List<InterviewRetrievalCase> cases) throws Exception {
            String apiKey = firstNonBlank(System.getenv("AI_BAILIAN_API_KEY"), System.getenv("OPENAI_API_KEY"));
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("缺少真实 Embedding 所需的 AI_BAILIAN_API_KEY / OPENAI_API_KEY");
            }

            RagProperties properties = new RagProperties();
            properties.setEnabled(true);
            properties.setHost("localhost");
            properties.setPort(6334);
            properties.setCollectionName("interview_eval_" + UUID.randomUUID());
            properties.setTopK(5);
            properties.setMinScore(0.65d);
            properties.setInitializeSchema(true);

            QdrantClient qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder(
                    properties.getHost(),
                    properties.getPort(),
                    false
            ).build());
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(firstNonBlank(System.getenv("OPENAI_BASE_URL"), DEFAULT_OPENAI_BASE_URL))
                    .apiKey(apiKey)
                    .build();
            OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(
                    openAiApi,
                    MetadataMode.EMBED,
                    OpenAiEmbeddingOptions.builder().model(DEFAULT_EMBEDDING_MODEL).build()
            );
            VectorStore vectorStore = QdrantVectorStore.builder(qdrantClient, embeddingModel)
                    .collectionName(properties.getCollectionName())
                    .initializeSchema(properties.isInitializeSchema())
                    .build();
            ((QdrantVectorStore) vectorStore).afterPropertiesSet();
            new KnowledgeIngestionService(vectorStore, properties).ingest(buildDocuments(cases));

            return new RealRetrievalHarness(
                    qdrantClient,
                    properties.getCollectionName(),
                    new RagRetrievalServiceImpl(vectorStore, qdrantClient, properties)
            );
        }

        private StageResult evaluateStages(InterviewRetrievalCase sample, RagRetrievalRequest request) {
            @SuppressWarnings("unchecked")
            List<Object> denseCandidates = (List<Object>) ReflectionTestUtils.invokeMethod(retrievalService, "denseRecall", request);
            @SuppressWarnings("unchecked")
            List<Object> sparseCandidates = (List<Object>) ReflectionTestUtils.invokeMethod(retrievalService, "sparseRecall", request);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> fusedCandidates = (java.util.Map<String, Object>) ReflectionTestUtils.invokeMethod(
                    retrievalService,
                    "fuseByRrf",
                    denseCandidates,
                    sparseCandidates
            );
            @SuppressWarnings("unchecked")
            List<Object> rerankedCandidates = (List<Object>) ReflectionTestUtils.invokeMethod(
                    retrievalService,
                    "rerank",
                    fusedCandidates.values(),
                    request
            );

            return new StageResult(
                    containsQuestionId(denseCandidates, sample.traceId()),
                    containsQuestionId(sparseCandidates, sample.traceId()),
                    containsQuestionId(fusedCandidates.values(), sample.traceId()),
                    containsQuestionId(rerankedCandidates, sample.traceId())
            );
        }

        private static boolean containsQuestionId(Collection<?> candidates, String expectedQuestionId) {
            return candidates.stream()
                    .map(candidate -> (String) ReflectionTestUtils.getField(candidate, "questionId"))
                    .anyMatch(expectedQuestionId::equals);
        }

        private static List<KnowledgeDocument> buildDocuments(List<InterviewRetrievalCase> cases) {
            List<KnowledgeDocument> documents = new ArrayList<>();
            for (InterviewRetrievalCase sample : cases) {
                if (!sample.shouldRetrieve()) {
                    continue;
                }
                RetrievalPlan retrievalPlan = sample.retrievalPlans().getFirst();
                documents.add(KnowledgeDocument.builder()
                        .id(sample.traceId())
                        .questionText(firstNonBlank(retrievalPlan.displayQuery(), sample.focusPoint()))
                        .intentConcept(firstNonBlank(retrievalPlan.goal(), sample.focusPoint()))
                        .referenceContext(firstNonBlank(retrievalPlan.queryText(), sample.focusPoint()))
                        .scoringKeyPoints(sample.mustHaveClues())
                        .scoringPitfalls(sample.avoidClues())
                        .followUpIds(sample.expectedFollowUpIds())
                        .domainCode(inferDomainCodeStatic(sample))
                        .questionType(normalizeQuestionTypeStatic(sample.questionType()))
                        .difficulty(firstNonBlank(sample.difficultyHint(), "L3"))
                        .keywords(sample.expectedKeywords())
                        .source("rag_eval")
                        .active(true)
                        .version("eval-v1")
                        .build());
            }
            return documents;
        }

        private static String normalizeQuestionTypeStatic(String questionType) {
            return "PROJECT".equalsIgnoreCase(questionType) ? "PROJECT" : questionType.trim().toUpperCase(Locale.ROOT);
        }

        private static String inferDomainCodeStatic(InterviewRetrievalCase sample) {
            String focus = (sample.focusPoint() + " " + String.join(" ", sample.expectedKeywords())).toLowerCase(Locale.ROOT);
            if ("BEHAVIORAL".equals(normalizeQuestionTypeStatic(sample.questionType()))) {
                return "";
            }
            if (focus.contains("redis") || focus.contains("redisson") || focus.contains("缓存")) {
                return "redis";
            }
            if (focus.contains("mysql") || focus.contains("innodb") || focus.contains("索引")) {
                return "mysql";
            }
            if (focus.contains("hashmap") || focus.contains("java")) {
                return "java_core";
            }
            if (focus.contains("mq") || focus.contains("seata") || focus.contains("xid") || focus.contains("订单")) {
                return "distributed";
            }
            return "";
        }

        @Override
        public void close() throws Exception {
            try {
                Boolean exists = qdrantClient.collectionExistsAsync(collectionName).get(3, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(exists)) {
                    qdrantClient.deleteCollectionAsync(collectionName).get(5, TimeUnit.SECONDS);
                }
            } finally {
                qdrantClient.close();
            }
        }

        private static String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) {
                return first;
            }
            return second == null ? "" : second;
        }
    }
}
