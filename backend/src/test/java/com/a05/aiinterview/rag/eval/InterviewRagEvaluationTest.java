package com.a05.aiinterview.rag.eval;

import com.a05.aiinterview.ai.dto.EvaluationDecisionOutput;
import com.a05.aiinterview.interview.engine.DecisionExecutionPlan;
import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.KnowledgeIngestionService;
import com.a05.aiinterview.rag.service.RagPlanCompiler;
import com.a05.aiinterview.rag.service.RagRerankService;
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
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Path BASELINE_REPORT = Path.of(
            "D:\\a05-cursor\\docs\\superpowers\\reports\\2026-03-28-rag-lightweight-hardening-baseline.md");

    private final RagPlanCompiler compiler = new RagPlanCompiler();

    @Test
    @DisplayName("fixture should contain the required sample skeleton")
    void fixture_shouldContainRequiredSampleSkeleton() throws Exception {
        InterviewRetrievalFixture fixture = loadFixture();

        assertThat(fixture.schemaVersion()).isEqualTo(1);
        assertThat(fixture.cases()).hasSizeGreaterThanOrEqualTo(16);
        assertThat(fixture.cases()).anyMatch(sample -> !sample.shouldRetrieve());
        assertThat(fixture.cases()).anyMatch(sample -> "PROJECT".equals(sample.questionType()));
        assertThat(fixture.cases()).anyMatch(sample -> "session-83_q-215_attempt-threadlocal-cross-thread".equals(sample.traceId()));
        assertThat(fixture.cases()).anyMatch(sample -> "session-84_q-218_attempt-generic-project-architecture".equals(sample.traceId()));
        assertThat(fixture.cases()).anyMatch(sample -> "session-85_q-221_attempt-redisson-project-watchdog".equals(sample.traceId()));
        assertThat(fixture.cases()).anyMatch(sample -> "session-86_q-224_attempt-behavior-push-hard-problem".equals(sample.traceId()));

        for (InterviewRetrievalCase sample : fixture.cases()) {
            assertThat(sample.traceId()).isNotBlank();
            assertThat(sample.questionType()).isNotBlank();
            assertThat(sample.focusPoint()).isNotBlank();

            if (sample.shouldRetrieve()) {
                RetrievalPlan retrievalPlan = requireSingleRetrievalPlan(sample);
                assertThat(sample.expectedKeywords()).isNotEmpty();
                assertThat(retrievalPlan.queryText()).isNotBlank();
                assertThat(retrievalPlan.keywordHints()).containsAll(sample.expectedKeywords());
            } else {
                assertThat(sample.retrievalPlans()).isEmpty();
                assertThat(sample.expectedKeywords()).isEmpty();
                assertThat(sample.expectedFollowUpIds()).isEmpty();
            }
        }

        assertThat(RETRIEVAL_COMPILE_BUDGET_MS).as("fixed compile budget, in ms").isEqualTo(10L);
        assertThat(SINGLE_RETRIEVAL_P95_BUDGET_MS).as("fixed single retrieval p95 budget, in ms").isEqualTo(300L);
        assertThat(END_TO_END_EXTRA_LATENCY_BUDGET_MS).as("fixed end-to-end extra latency budget, in ms").isEqualTo(500L);
    }

    @Test
    @DisplayName("baseline report should exist and explain current bottlenecks")
    void baselineReport_shouldExistAndExplainCurrentBottlenecks() throws Exception {
        assertThat(Files.exists(BASELINE_REPORT))
                .as("missing baseline report: %s", BASELINE_REPORT)
                .isTrue();

        String report = Files.readString(BASELINE_REPORT);
        assertThat(report).contains("当前 dense 基线是否稳定");
        assertThat(report).contains("当前候选是否足以支撑默认商业 rerank");
        assertThat(report).contains("当前最容易退化的题型");
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
            assertThat(report.structuralPollutionRate())
                    .withFailMessage(report.failureSummary(DENSE_RECALL_HIT_RATE_GATE))
                    .isLessThanOrEqualTo(0.2d);
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
                    new StageResult(false, false, false),
                    false,
                    false,
                    false
            );
        }

        StageResult stageResult = harness.evaluateStages(sample, compiled);
        RagContext context = harness.retrievalService.retrieve(compiled);
        boolean retrieved = containsQuestionId(context.getRetrievedMaterials(), sample.traceId());
        boolean structuralPolluted = hasStructuralPollution(context, sample);
        boolean followUpMatched = hasExpectedFollowUp(context, sample);

        return new SampleEvaluationResult(
                sample.traceId(),
                true,
                routingMatched,
                stageResult,
                retrieved,
                structuralPolluted,
                followUpMatched
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
                        .queryText(plan.queryText())
                        .keywordHints(plan.keywordHints())
                        .difficultyHint(plan.difficultyHint())
                        .build())
                .toList();
    }

    private boolean containsQuestionId(List<RagContext.RetrievedMaterial> materials, String expectedQuestionId) {
        return materials.stream().anyMatch(material -> Objects.equals(expectedQuestionId, material.getQuestionId()));
    }

    private boolean hasStructuralPollution(RagContext context, InterviewRetrievalCase sample) {
        String expectedQuestionType = normalizeQuestionType(sample.questionType());
        String expectedDomainCode = inferDomainCode(sample);
        return context.getRetrievedMaterials().stream()
                .filter(material -> !Objects.equals(sample.traceId(), material.getQuestionId()))
                .anyMatch(material -> isStructurallyPolluting(material, expectedQuestionType, expectedDomainCode));
    }

    private boolean hasExpectedFollowUp(RagContext context, InterviewRetrievalCase sample) {
        if (sample.expectedFollowUpIds() == null || sample.expectedFollowUpIds().isEmpty()) {
            return true;
        }
        return context.getFollowUpCandidates().stream()
                .anyMatch(sample.expectedFollowUpIds()::contains);
    }

    private boolean isStructurallyPolluting(
            RagContext.RetrievedMaterial material,
            String expectedQuestionType,
            String expectedDomainCode
    ) {
        String actualQuestionType = normalizeQuestionType(material.getQuestionType());
        String actualDomainCode = normalize(material.getDomainCode());
        String normalizedExpectedDomainCode = normalize(expectedDomainCode);

        if ("BEHAVIORAL".equals(expectedQuestionType)) {
            return !"BEHAVIORAL".equals(actualQuestionType) || !actualDomainCode.isBlank();
        }
        if ("PROJECT_DEEP_DIVE".equals(expectedQuestionType)) {
            return !"PROJECT".equals(actualQuestionType) && !"PROJECT_DEEP_DIVE".equals(actualQuestionType);
        }
        if (!actualQuestionType.equals(expectedQuestionType)) {
            return true;
        }
        if (normalizedExpectedDomainCode.isBlank()) {
            return !actualDomainCode.isBlank();
        }
        return !normalizedExpectedDomainCode.equals(actualDomainCode);
    }

    private EvaluationReport aggregate(List<SampleEvaluationResult> sampleResults) {
        int total = sampleResults.size();
        List<String> routeMismatchTraceIds = new ArrayList<>();
        List<String> denseRecallMissTraceIds = new ArrayList<>();
        List<String> structuralPollutionTraceIds = new ArrayList<>();
        List<String> followUpMissTraceIds = new ArrayList<>();

        int routingMatches = 0;
        int retrievableSamples = 0;
        int retrievedSamples = 0;
        int structuralPollutionHits = 0;
        int denseRecallHits = 0;
        int sparseRecallHits = 0;
        int rerankHits = 0;
        int followUpExpectedSamples = 0;
        int followUpHits = 0;

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
                if (result.structuralPolluted()) {
                    structuralPollutionHits++;
                    structuralPollutionTraceIds.add(result.traceId());
                }
                if (result.stageResult().denseRecallHit()) {
                    denseRecallHits++;
                } else {
                    denseRecallMissTraceIds.add(result.traceId());
                }
                if (result.stageResult().lexicalPrefilterHit()) {
                    sparseRecallHits++;
                }
                if (result.stageResult().rerankHit()) {
                    rerankHits++;
                }
                if (result.followUpMatched() || !result.retrieved()) {
                    followUpHits++;
                } else {
                    followUpMissTraceIds.add(result.traceId());
                }
                followUpExpectedSamples++;
            }
        }

        double routingAccuracy = total == 0 ? 1.0d : (double) routingMatches / total;
        double retrievalApplicableHitRate = retrievableSamples == 0 ? 1.0d : (double) retrievedSamples / retrievableSamples;
        double structuralPollutionRate = retrievableSamples == 0 ? 0.0d : (double) structuralPollutionHits / retrievableSamples;
        double denseRecallHitRate = retrievableSamples == 0 ? 0.0d : (double) denseRecallHits / retrievableSamples;
        double lexicalPrefilterHitRate = retrievableSamples == 0 ? 0.0d : (double) sparseRecallHits / retrievableSamples;
        double rerankTop3HitRate = retrievableSamples == 0 ? 0.0d : (double) rerankHits / retrievableSamples;
        double followUpHitRate = followUpExpectedSamples == 0 ? 1.0d : (double) followUpHits / followUpExpectedSamples;

        return new EvaluationReport(
                total,
                routeMismatchTraceIds,
                denseRecallMissTraceIds,
                structuralPollutionTraceIds,
                followUpMissTraceIds,
                routingAccuracy,
                retrievalApplicableHitRate,
                structuralPollutionRate,
                denseRecallHitRate,
                lexicalPrefilterHitRate,
                rerankTop3HitRate,
                followUpHitRate
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
            List<String> expectedFollowUpIds,
            List<RetrievalPlan> retrievalPlans
    ) {
    }

    private record RetrievalPlan(
            String queryText,
            List<String> keywordHints,
            String difficultyHint
    ) {
    }

    private record EvaluationReport(
            int totalSamples,
            List<String> routeMismatchTraceIds,
            List<String> denseRecallMissTraceIds,
            List<String> structuralPollutionTraceIds,
            List<String> followUpMissTraceIds,
            double routingAccuracy,
            double retrievalApplicableHitRate,
            double structuralPollutionRate,
            double denseRecallHitRate,
            double lexicalPrefilterHitRate,
            double rerankTop3HitRate,
            double followUpHitRate
    ) {
        private String failureSummary(double gate) {
            return """
                    Interview RAG evaluation gate failed
                    totalSamples=%d
                    routeMismatchTraceIds=%s
                    denseRecallMissTraceIds=%s
                    structuralPollutionTraceIds=%s
                    followUpMissTraceIds=%s
                    metrics={routingAccuracy=%.3f, retrievalApplicableHitRate=%.3f, structuralPollutionRate=%.3f, denseRecallHitRate=%.3f, lexicalPrefilterHitRate=%.3f, rerankTop3HitRate=%.3f, followUpHitRate=%.3f}
                    gate=denseRecallHitRate>=%.1f
                    """.formatted(
                    totalSamples,
                    routeMismatchTraceIds,
                    denseRecallMissTraceIds,
                    structuralPollutionTraceIds,
                    followUpMissTraceIds,
                    routingAccuracy,
                    retrievalApplicableHitRate,
                    structuralPollutionRate,
                    denseRecallHitRate,
                    lexicalPrefilterHitRate,
                    rerankTop3HitRate,
                    followUpHitRate,
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
            boolean structuralPolluted,
            boolean followUpMatched
    ) {
    }

    private record StageResult(
            boolean lexicalPrefilterHit,
            boolean denseRecallHit,
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
                    new RagRetrievalServiceImpl(vectorStore, qdrantClient, new TestRagRerankService(), properties)
            );
        }

        private StageResult evaluateStages(InterviewRetrievalCase sample, RagRetrievalRequest request) {
            @SuppressWarnings("unchecked")
            List<Object> lexicalCandidates = (List<Object>) ReflectionTestUtils.invokeMethod(retrievalService, "lexicalPrefilter", request);
            @SuppressWarnings("unchecked")
            List<Object> denseCandidates = (List<Object>) ReflectionTestUtils.invokeMethod(
                    retrievalService,
                    "denseRecall",
                    request,
                    lexicalCandidates
            );
            @SuppressWarnings("unchecked")
            List<Object> rerankedCandidates = (List<Object>) ReflectionTestUtils.invokeMethod(
                    retrievalService,
                    "rerank",
                    denseCandidates,
                    request
            );

            return new StageResult(
                    containsQuestionId(lexicalCandidates, sample.traceId()),
                    containsQuestionId(denseCandidates, sample.traceId()),
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
                        .questionText(sample.focusPoint())
                        .intentConcept(sample.focusPoint())
                        .referenceContext(firstNonBlank(retrievalPlan.queryText(), sample.focusPoint()))
                        .scoringKeyPoints(sample.expectedKeywords())
                        .scoringPitfalls(List.of())
                        .followUpIds(sample.expectedFollowUpIds())
                        .domainCode(inferDomainCodeStatic(sample))
                        .questionType(normalizeQuestionTypeStatic(sample.questionType()))
                        .difficulty(firstNonBlank(retrievalPlan.difficultyHint(), firstNonBlank(sample.difficultyHint(), "L3")))
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

        private static final class TestRagRerankService implements RagRerankService {
            @Override
            public List<RerankResult> rerank(RagRetrievalRequest request, List<RerankCandidate> candidates) {
                List<RerankResult> results = new ArrayList<>();
                for (int i = 0; i < candidates.size(); i++) {
                    results.add(new RerankResult(candidates.get(i).questionId(), 1.0d / (i + 1)));
                }
                return results;
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
