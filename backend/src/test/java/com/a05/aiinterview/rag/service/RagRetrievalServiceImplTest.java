package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.qdrant.QdrantHybridQueryExecutor;
import com.a05.aiinterview.rag.qdrant.RrfFusion;
import com.a05.aiinterview.rag.service.RagRerankService.RerankResult;
import com.a05.aiinterview.rag.service.impl.RagRetrievalServiceImpl;
import com.google.common.util.concurrent.Futures;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RagRetrievalServiceImpl tests")
class RagRetrievalServiceImplTest {

    @Test
    @DisplayName("retrieve should short circuit when shouldRetrieve is false")
    void retrieve_shouldShortCircuitWhenShouldRetrieveIsFalse() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService);

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(false)
                .build());

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.getRetrievalAudit()).isNotNull();
        assertThat(context.getRetrievalAudit().isRetrievalTriggered()).isFalse();
        verify(qdrantClient, never()).queryAsync(any(Points.QueryPoints.class));
        verify(rerankService, never()).rerank(any(), any());
    }

    @Test
    @DisplayName("retrieve should execute dense and sparse branches independently then rerank fused candidates")
    void retrieve_shouldExecuteDenseAndSparseBranchesIndependentlyThenRerankFusedCandidates() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService, true);

        when(embeddingModel.embed("Redis 缓存穿透的原理与防护")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                1L, "redis-null-cache-expire-001", "缓存空对象应该怎么设置过期时间",
                                "考察空对象缓存的 TTL 和一致性权衡", "空对象缓存不能永久保留，需要结合 TTL 做权衡。",
                                List.of("TTL", "脏数据窗口"), List.of("TTL 一刀切"), List.of("follow-ttl"),
                                "redis", "PRINCIPLE", "L3", List.of("Redis", "空对象缓存"), 0.91f
                        ),
                        scoredPoint(
                                2L, "redis-cache-penetration-001", "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器", "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"), List.of("混淆缓存击穿和穿透"), List.of("follow-penetration"),
                                "redis", "PRINCIPLE", "L2", List.of("Redis", "缓存穿透", "布隆过滤器"), 0.86f
                        )
                )))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                3L, "redis-cache-penetration-001", "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器", "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"), List.of("混淆缓存击穿和穿透"), List.of("follow-penetration"),
                                "redis", "PRINCIPLE", "L2", List.of("Redis", "缓存穿透", "布隆过滤器"), 0.77f
                        ),
                        scoredPoint(
                                4L, "redis-bloom-filter-001", "布隆过滤器误判怎么处理",
                                "考察误判率和降级策略", "需要说明误判对业务的影响和兜底方案。",
                                List.of("误判率", "降级"), List.of("把布隆过滤器当成绝对正确"), List.of("follow-bloom"),
                                "redis", "PRINCIPLE", "L3", List.of("布隆过滤器", "误判"), 0.66f
                        )
                )));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("redis-bloom-filter-001", 0.99d),
                new RerankResult("redis-cache-penetration-001", 0.88d),
                new RerankResult("redis-null-cache-expire-001", 0.30d)
        ));

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透的原理与防护")
                .denseQueryText("Redis 缓存穿透的原理与防护")
                .sparseQueryText("Redis 缓存穿透 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("缓存穿透")
                .build();

        RagContext context = service.retrieve(request);

        ArgumentCaptor<Points.QueryPoints> queryCaptor = ArgumentCaptor.forClass(Points.QueryPoints.class);
        verify(qdrantClient, org.mockito.Mockito.times(2)).queryAsync(queryCaptor.capture());
        List<Points.QueryPoints> requests = queryCaptor.getAllValues();
        Points.QueryPoints denseRequest = requests.get(0);
        Points.QueryPoints sparseRequest = requests.get(1);

        assertThat(denseRequest.getUsing()).isEqualTo("dense");
        assertThat(denseRequest.getQuery().hasNearest()).isTrue();
        assertThat(denseRequest.getQuery().getNearest().hasDense()).isTrue();
        assertThat(denseRequest.getQuery().getNearest().getDense().getDataList()).containsExactly(0.1f, 0.2f);
        assertThat(denseRequest.getFilter().toString())
                .contains("active")
                .contains("question_type")
                .contains("difficulty")
                .contains("L1")
                .contains("L2")
                .contains("L3")
                .doesNotContain("domain_code");

        assertThat(sparseRequest.getUsing()).isEqualTo("bm25");
        assertThat(sparseRequest.getQuery().hasNearest()).isTrue();
        assertThat(sparseRequest.getQuery().getNearest().hasDocument()).isTrue();
        assertThat(sparseRequest.getQuery().getNearest().getDocument().getText()).isEqualTo("Redis 缓存穿透 布隆过滤器");
        assertThat(sparseRequest.getQuery().getNearest().getDocument().getModel()).isEqualTo("qdrant/bm25");
        assertThat(sparseRequest.getFilter().toString())
                .contains("active")
                .contains("question_type")
                .contains("difficulty")
                .contains("L1")
                .contains("L2")
                .contains("L3")
                .doesNotContain("domain_code");

        assertThat(context.isEmpty()).isFalse();
        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("redis-bloom-filter-001", "redis-cache-penetration-001", "redis-null-cache-expire-001");
        assertThat(context.getRetrievalAudit())
                .extracting(
                        "difficultyWindowApplied",
                        "difficultyWindowValues",
                        "denseCandidateCount",
                        "sparseCandidateCount"
                )
                .containsExactly(true, List.of("L1", "L2", "L3"), 2, 2);
        assertThat(context.getRetrievalAudit().getFusionTopQuestionIds())
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001", "redis-bloom-filter-001");
        assertThat(context.getRetrievalAudit().getRerankPreTopQuestionIds())
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001", "redis-bloom-filter-001");
        assertThat(context.getRetrievalAudit().getRerankPostTopQuestionIds())
                .containsExactly("redis-bloom-filter-001", "redis-cache-penetration-001", "redis-null-cache-expire-001");
        assertThat(context.getRetrievalAudit().getInjectedQuestionIds())
                .containsExactly("redis-bloom-filter-001", "redis-cache-penetration-001", "redis-null-cache-expire-001");
    }

    @Test
    @DisplayName("retrieve should not append difficulty filter when window switch is disabled")
    void retrieve_shouldNotAppendDifficultyFilterWhenWindowSwitchIsDisabled() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService, false);

        when(embeddingModel.embed("Redis 缓存穿透的原理与防护")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of()))
                .thenReturn(Futures.immediateFuture(List.of()));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透的原理与防护")
                .denseQueryText("Redis 缓存穿透的原理与防护")
                .sparseQueryText("Redis 缓存穿透 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("缓存穿透")
                .build());

        ArgumentCaptor<Points.QueryPoints> queryCaptor = ArgumentCaptor.forClass(Points.QueryPoints.class);
        verify(qdrantClient, org.mockito.Mockito.times(2)).queryAsync(queryCaptor.capture());
        List<Points.QueryPoints> requests = queryCaptor.getAllValues();

        assertThat(requests.get(0).getFilter().toString())
                .contains("active")
                .contains("question_type")
                .doesNotContain("difficulty");
        assertThat(requests.get(1).getFilter().toString())
                .contains("active")
                .contains("question_type")
                .doesNotContain("difficulty");
        assertThat(context.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("retrieve should keep difficulty audit when recall throws exception")
    void retrieve_shouldKeepDifficultyAuditWhenRecallThrowsException() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService, true);

        when(embeddingModel.embed("Redis 缓存穿透的原理与防护")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenThrow(new IllegalStateException("qdrant down"));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透的原理与防护")
                .denseQueryText("Redis 缓存穿透的原理与防护")
                .sparseQueryText("Redis 缓存穿透 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("缓存穿透")
                .build());

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.getRetrievalAudit())
                .extracting("retrievalTriggered", "difficultyWindowApplied", "difficultyWindowValues")
                .containsExactly(true, true, List.of("L1", "L2", "L3"));
    }

    @Test
    @DisplayName("retrieve should preserve known audit state when sparse recall throws after dense succeeds")
    void retrieve_shouldPreserveKnownAuditStateWhenSparseRecallThrowsAfterDenseSucceeds() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService, true);

        when(embeddingModel.embed("Redis 缓存穿透的原理与防护")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                1L, "redis-cache-penetration-001", "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器", "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"), List.of("混淆缓存击穿和穿透"), List.of("follow-penetration"),
                                "redis", "PRINCIPLE", "L2", List.of("Redis", "缓存穿透", "布隆过滤器"), 0.86f
                        )
                )))
                .thenThrow(new IllegalStateException("qdrant down"));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透的原理与防护")
                .denseQueryText("Redis 缓存穿透的原理与防护")
                .sparseQueryText("Redis 缓存穿透 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("缓存穿透")
                .build());

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.getRetrievalAudit())
                .extracting(
                        "retrievalTriggered",
                        "denseCandidateCount",
                        "sparseCandidateCount",
                        "difficultyWindowApplied",
                        "difficultyWindowValues",
                        "fusionTopQuestionIds"
                )
                .containsExactly(true, 1, 0, true, List.of("L1", "L2", "L3"), List.of());
    }

    @Test
    @DisplayName("retrieve audit should keep full fusion and rerank input candidates when they exceed injected topk")
    void retrieveAudit_shouldKeepFullFusionAndRerankInputCandidatesWhenTheyExceedInjectedTopk() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService, 2, 4);

        when(embeddingModel.embed("Redis 缓存击穿与穿透保护")).thenReturn(new float[]{0.2f, 0.3f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(41L, "q1", "题目1", "考点1", "语境1", List.of("kp1"), List.of(), List.of(), "redis", "PRINCIPLE", "L2", List.of("k1"), 0.91f),
                        scoredPoint(42L, "q2", "题目2", "考点2", "语境2", List.of("kp2"), List.of(), List.of(), "redis", "PRINCIPLE", "L2", List.of("k2"), 0.89f),
                        scoredPoint(43L, "q3", "题目3", "考点3", "语境3", List.of("kp3"), List.of(), List.of(), "redis", "PRINCIPLE", "L2", List.of("k3"), 0.87f)
                )))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(44L, "q2", "题目2", "考点2", "语境2", List.of("kp2"), List.of(), List.of(), "redis", "PRINCIPLE", "L2", List.of("k2"), 0.86f),
                        scoredPoint(45L, "q4", "题目4", "考点4", "语境4", List.of("kp4"), List.of(), List.of(), "redis", "PRINCIPLE", "L2", List.of("k4"), 0.85f)
                )));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("q4", 0.99d),
                new RerankResult("q2", 0.88d),
                new RerankResult("q1", 0.77d),
                new RerankResult("q3", 0.66d)
        ));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存击穿与穿透保护")
                .denseQueryText("Redis 缓存击穿与穿透保护")
                .sparseQueryText("Redis 缓存击穿 穿透")
                .keywordQueries(List.of("Redis", "缓存击穿", "穿透"))
                .questionType("PRINCIPLE")
                .focusPoint("缓存保护")
                .build());

        assertThat(context.getRetrievalAudit().getFusionTopQuestionIds())
                .containsExactly("q2", "q1", "q4", "q3");
        assertThat(context.getRetrievalAudit().getRerankPreTopQuestionIds())
                .containsExactly("q2", "q1", "q4", "q3");
        assertThat(context.getRetrievalAudit().getRerankPostTopQuestionIds())
                .containsExactly("q4", "q2");
        assertThat(context.getRetrievalAudit().getInjectedQuestionIds())
                .containsExactly("q4", "q2");
    }

    @Test
    @DisplayName("retrieve should skip sparse branch when sparse query text is blank")
    void retrieve_shouldSkipSparseBranchWhenSparseQueryTextIsBlank() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService);

        when(embeddingModel.embed("行为面试里的冲突协作")).thenReturn(new float[]{0.3f, 0.4f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                11L, "behavior-conflict-001", "讲一次你和产品意见不一致的经历",
                                "考察冲突沟通和推进结果", "重点看你如何推动决策和复盘。",
                                List.of("个人动作", "推进过程"), List.of("空泛价值观表态"), List.of("behavior-follow"),
                                "", "BEHAVIORAL", "L2", List.of("沟通", "推进"), 0.87f
                        )
                )));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("behavior-conflict-001", 0.91d)
        ));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("行为面试里的冲突协作")
                .denseQueryText("行为面试里的冲突协作")
                .sparseQueryText("")
                .keywordQueries(List.of())
                .questionType("BEHAVIORAL")
                .focusPoint("与产品意见不一致")
                .build());

        verify(qdrantClient, org.mockito.Mockito.times(1)).queryAsync(any(Points.QueryPoints.class));
        assertThat(context.isEmpty()).isFalse();
        assertThat(context.getRetrievalAudit().getDenseCandidateCount()).isEqualTo(1);
        assertThat(context.getRetrievalAudit().getSparseCandidateCount()).isEqualTo(0);
        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("behavior-conflict-001");
    }

    @Test
    @DisplayName("retrieve should keep behavioral guardrails even when rerank prefers technical cards")
    void retrieve_shouldKeepBehavioralGuardrailsEvenWhenRerankPrefersTechnicalCards() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService);

        when(embeddingModel.embed("行为面试 冲突沟通")).thenReturn(new float[]{0.5f, 0.6f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                21L, "behavior-conflict-001", "讲一次你和产品意见不一致的经历",
                                "考察冲突沟通和推进结果", "重点看你如何推动决策和复盘。",
                                List.of("个人动作", "推进过程"), List.of("空泛价值观表态"), List.of("behavior-follow"),
                                "", "BEHAVIORAL", "L2", List.of("沟通", "推进"), 0.88f
                        ),
                        scoredPoint(
                                22L, "redis-cache-penetration-001", "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器", "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"), List.of("混淆缓存击穿和穿透"), List.of("follow-penetration"),
                                "redis", "PRINCIPLE", "L2", List.of("Redis", "缓存穿透"), 0.92f
                        )
                )))
                .thenReturn(Futures.immediateFuture(List.of()));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("redis-cache-penetration-001", 0.99d),
                new RerankResult("behavior-conflict-001", 0.20d)
        ));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("行为面试 冲突沟通")
                .denseQueryText("行为面试 冲突沟通")
                .sparseQueryText("")
                .keywordQueries(List.of())
                .questionType("BEHAVIORAL")
                .focusPoint("跨团队协作")
                .build());

        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("behavior-conflict-001");
    }

    @Test
    @DisplayName("retrieve should fall back to fusion order when rerank fails")
    void retrieve_shouldFallBackToFusionOrderWhenRerankFails() throws Exception {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(embeddingModel, qdrantClient, rerankService);

        when(embeddingModel.embed("Redis 缓存穿透")).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.queryAsync(any(Points.QueryPoints.class)))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                31L, "redis-null-cache-expire-001", "缓存空对象应该怎么设置过期时间",
                                "考察空对象缓存的 TTL", "需要权衡 TTL 和脏数据窗口。",
                                List.of("TTL"), List.of("TTL 一刀切"), List.of(), "redis",
                                "PRINCIPLE", "L3", List.of("空对象缓存"), 0.93f
                        ),
                        scoredPoint(
                                32L, "redis-cache-penetration-001", "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器", "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"), List.of("混淆缓存击穿和穿透"), List.of(), "redis",
                                "PRINCIPLE", "L2", List.of("Redis", "缓存穿透"), 0.91f
                        )
                )))
                .thenReturn(Futures.immediateFuture(List.of(
                        scoredPoint(
                                33L, "redis-cache-penetration-001", "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器", "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"), List.of("混淆缓存击穿和穿透"), List.of(), "redis",
                                "PRINCIPLE", "L2", List.of("Redis", "缓存穿透"), 0.81f
                        )
                )));
        when(rerankService.rerank(any(), any())).thenThrow(new IllegalStateException("rerank down"));

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透")
                .denseQueryText("Redis 缓存穿透")
                .sparseQueryText("Redis 缓存穿透")
                .keywordQueries(List.of("Redis", "缓存穿透"))
                .questionType("PRINCIPLE")
                .focusPoint("缓存穿透")
                .build());

        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001");
        assertThat(context.getRetrievalAudit().getRerankPostTopQuestionIds())
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001");
    }

    private RagRetrievalServiceImpl newService(EmbeddingModel embeddingModel,
                                               QdrantClient qdrantClient,
                                               RagRerankService rerankService) {
        return newService(embeddingModel, qdrantClient, rerankService, 3, 3);
    }

    private RagRetrievalServiceImpl newService(EmbeddingModel embeddingModel,
                                               QdrantClient qdrantClient,
                                               RagRerankService rerankService,
                                               boolean difficultyWindowEnabled) {
        RagProperties properties = new RagProperties();
        properties.setCollectionName("interview_knowledge_hybrid");
        properties.setDenseVectorName("dense");
        properties.setSparseVectorName("bm25");
        properties.setDenseTopK(3);
        properties.setSparseTopK(3);
        properties.setFusionTopK(3);
        properties.setTopK(3);
        properties.setMinScore(0.0d);
        configureDifficultyWindow(properties, difficultyWindowEnabled);
        QdrantHybridQueryExecutor queryExecutor = new QdrantHybridQueryExecutor(embeddingModel, qdrantClient, properties);
        return new RagRetrievalServiceImpl(queryExecutor, new RrfFusion(), rerankService, properties);
    }

    private RagRetrievalServiceImpl newService(EmbeddingModel embeddingModel,
                                               QdrantClient qdrantClient,
                                               RagRerankService rerankService,
                                               int topK,
                                               int fusionTopK) {
        RagProperties properties = new RagProperties();
        properties.setCollectionName("interview_knowledge_hybrid");
        properties.setDenseVectorName("dense");
        properties.setSparseVectorName("bm25");
        properties.setDenseTopK(3);
        properties.setSparseTopK(3);
        properties.setFusionTopK(fusionTopK);
        properties.setTopK(topK);
        properties.setMinScore(0.0d);
        QdrantHybridQueryExecutor queryExecutor = new QdrantHybridQueryExecutor(embeddingModel, qdrantClient, properties);
        return new RagRetrievalServiceImpl(queryExecutor, new RrfFusion(), rerankService, properties);
    }

    private void configureDifficultyWindow(RagProperties properties, boolean enabled) {
        try {
            java.lang.reflect.Field field = RagProperties.class.getDeclaredField("difficultyWindowEnabled");
            field.setAccessible(true);
            field.setBoolean(properties, enabled);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError("RagProperties should expose difficultyWindowEnabled", e);
        }
    }

    private Points.ScoredPoint scoredPoint(long pointId,
                                           String questionId,
                                           String questionText,
                                           String intentConcept,
                                           String referenceContext,
                                           List<String> scoringKeyPoints,
                                           List<String> scoringPitfalls,
                                           List<String> followUpIds,
                                           String domainCode,
                                           String questionType,
                                           String difficulty,
                                           List<String> keywords,
                                           float score) {
        return Points.ScoredPoint.newBuilder()
                .setId(PointIdFactory.id(pointId))
                .setScore(score)
                .putPayload("question_id", ValueFactory.value(questionId))
                .putPayload("question_text", ValueFactory.value(questionText))
                .putPayload("intent_concept", ValueFactory.value(intentConcept))
                .putPayload("reference_context", ValueFactory.value(referenceContext))
                .putPayload("scoring_key_points", ValueFactory.value(scoringKeyPoints.stream().map(ValueFactory::value).toList()))
                .putPayload("scoring_pitfalls", ValueFactory.value(scoringPitfalls.stream().map(ValueFactory::value).toList()))
                .putPayload("follow_up_ids", ValueFactory.value(followUpIds.stream().map(ValueFactory::value).toList()))
                .putPayload("domain_code", ValueFactory.value(domainCode))
                .putPayload("question_type", ValueFactory.value(questionType))
                .putPayload("difficulty", ValueFactory.value(difficulty))
                .putPayload("keywords", ValueFactory.value(keywords.stream().map(ValueFactory::value).toList()))
                .putPayload("active", ValueFactory.value(true))
                .build();
    }
}
