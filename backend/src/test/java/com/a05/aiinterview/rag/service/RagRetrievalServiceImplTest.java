package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRerankService.RerankCandidate;
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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RagRetrievalServiceImpl tests")
class RagRetrievalServiceImplTest {

    @Test
    @DisplayName("retrieve should use Qdrant lexical prefilter before dense recall and drop non-matching dense candidates")
    void retrieve_shouldUseQdrantLexicalPrefilterBeforeDenseRecallAndDropNonMatchingDenseCandidates() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                denseDoc(
                        "redis-install-001",
                        "Redis 安装和基础 API",
                        "基础 API 和安装步骤",
                        "部署教程，不涉及缓存穿透方案",
                        List.of("安装步骤"),
                        List.of("redis-install-follow-001"),
                        "redis",
                        "PRINCIPLE",
                        "L2",
                        List.of("Redis", "安装")
                ),
                denseDoc(
                        "redis-cache-penetration-001",
                        "讲一下 Redis 缓存穿透",
                        "考察空对象缓存和布隆过滤器",
                        "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底，并关注误判影响。",
                        List.of("空对象缓存", "布隆过滤器", "误判"),
                        List.of("redis-bloom-false-positive-001", "redis-null-cache-expire-001"),
                        "redis",
                        "PRINCIPLE",
                        "L2",
                        List.of("Redis", "缓存穿透", "布隆过滤器")
                )
        ));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(Futures.immediateFuture(
                Points.ScrollResponse.newBuilder()
                        .addResult(point(
                                101L,
                                "redis-cache-penetration-001",
                                "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器",
                                "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底，并关注误判影响。",
                                List.of("空对象缓存", "布隆过滤器", "误判"),
                                List.of("把缓存穿透和击穿混淆"),
                                List.of("redis-bloom-false-positive-001", "redis-null-cache-expire-001"),
                                "redis",
                                "PRINCIPLE",
                                "L2",
                                List.of("Redis", "缓存穿透", "布隆过滤器")
                        ))
                        .addResult(point(
                                102L,
                                "redis-null-cache-expire-001",
                                "缓存空对象应该怎么设置过期时间",
                                "考察空对象缓存的 TTL 和一致性权衡",
                                "空对象缓存不能永久保留，需要结合 TTL 和脏数据窗口做权衡。",
                                List.of("TTL", "脏数据窗口"),
                                List.of("TTL 一刀切"),
                                List.of("redis-null-cache-dirty-window-001"),
                                "redis",
                                "PRINCIPLE",
                                "L3",
                                List.of("Redis", "空对象缓存", "TTL")
                        ))
                        .build()
        ));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("redis-cache-penetration-001", 0.98d),
                new RerankResult("redis-null-cache-expire-001", 0.35d)
        ));

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透 兜底方案 空对象缓存 布隆过滤器 误判")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .build();

        RagContext context = service.retrieve(request);

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<Points.ScrollPoints> scrollCaptor = ArgumentCaptor.forClass(Points.ScrollPoints.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        verify(qdrantClient).scrollAsync(scrollCaptor.capture());

        SearchRequest denseRequest = searchCaptor.getValue();
        assertThat(denseRequest.getQuery()).isEqualTo(request.getQueryText());
        assertThat(denseRequest.getFilterExpression().toString())
                .contains("question_type")
                .contains("PRINCIPLE")
                .contains("domain_code")
                .contains("redis")
                .doesNotContain("difficulty");

        Points.ScrollPoints scrollPoints = scrollCaptor.getValue();
        assertThat(scrollPoints.getCollectionName()).isEqualTo("interview_knowledge");
        assertThat(filterMatches(scrollPoints.getFilter(), "question_type")).contains("PRINCIPLE");
        assertThat(filterMatches(scrollPoints.getFilter(), "domain_code")).contains("redis");
        assertThat(filterMatches(scrollPoints.getFilter(), "difficulty")).isEmpty();
        assertThat(scrollPoints.getFilter().hasMinShould()).isTrue();
        assertThat(scrollPoints.getFilter().getMinShould().getMinCount()).isEqualTo(1);

        assertThat(context.isEmpty()).isFalse();
        assertThat(context.getRetrievedMaterials()).hasSize(1);
        assertThat(context.getRetrievedMaterials().getFirst().getQuestionId()).isEqualTo("redis-cache-penetration-001");
        assertThat(context.getRetrievedMaterials().getFirst().getQuestionText()).contains("缓存穿透");
        assertThat(context.getSummary()).contains("Redis 缓存穿透");
        assertThat(context.getFollowUpCandidates())
                .contains("redis-bloom-false-positive-001", "redis-null-cache-expire-001")
                .doesNotContain("redis-install-follow-001");
        assertThat(context.getRetrievedMaterials().stream().map(RagContext.RetrievedMaterial::getQuestionId))
                .doesNotContain("redis-install-001", "redis-null-cache-expire-001");

        assertThat(minShouldTextMatches(scrollPoints.getFilter(), "question_text"))
                .contains("Redis", "缓存穿透", "布隆过滤器");
        assertThat(minShouldTextMatches(scrollPoints.getFilter(), "intent_concept"))
                .contains("Redis", "缓存穿透", "布隆过滤器");
        assertThat(minShouldKeywordMatches(scrollPoints.getFilter(), "keywords"))
                .contains("Redis", "缓存穿透", "布隆过滤器");
    }

    @Test
    @DisplayName("retrieve should allow behavioral lexical prefilter without domainCode filter")
    void retrieve_shouldAllowBehavioralLexicalPrefilterWithoutDomainCodeFilter() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(Futures.immediateFuture(
                Points.ScrollResponse.newBuilder().build()
        ));
        when(rerankService.rerank(any(), any())).thenReturn(List.of());

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("行为面试 与产品意见不一致 冲突沟通 推进结果 复盘")
                .keywordQueries(List.of("沟通", "推进", "冲突", "协作"))
                .domainCode("")
                .questionType("BEHAVIORAL")
                .difficultyHint("L2")
                .build();

        service.retrieve(request);

        ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        ArgumentCaptor<Points.ScrollPoints> scrollCaptor = ArgumentCaptor.forClass(Points.ScrollPoints.class);
        verify(vectorStore).similaritySearch(searchCaptor.capture());
        verify(qdrantClient).scrollAsync(scrollCaptor.capture());

        SearchRequest denseRequest = searchCaptor.getValue();
        assertThat(denseRequest.getFilterExpression().toString())
                .contains("question_type")
                .contains("BEHAVIORAL")
                .doesNotContain("domain_code");

        Points.ScrollPoints scrollPoints = scrollCaptor.getValue();
        assertThat(filterMatches(scrollPoints.getFilter(), "question_type")).contains("BEHAVIORAL");
        assertThat(filterMatches(scrollPoints.getFilter(), "domain_code")).isEmpty();
        assertThat(scrollPoints.getFilter().hasMinShould()).isTrue();
        assertThat(scrollPoints.getFilter().getMinShould().getMinCount()).isEqualTo(1);
        assertThat(minShouldTextMatches(scrollPoints.getFilter(), "question_text"))
                .contains("沟通", "推进", "冲突", "协作");
    }

    @Test
    @DisplayName("retrieve should short circuit when shouldRetrieve is false")
    void retrieve_shouldShortCircuitWhenShouldRetrieveIsFalse() {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(false)
                .build());

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.getRetrievedMaterials()).isEmpty();
        assertThat(context.getFollowUpCandidates()).isEmpty();
        assertThat(context.getRetrievalAudit()).isNotNull();
        assertThat(context.getRetrievalAudit().isRetrievalTriggered()).isFalse();
        verify(rerankService, never()).rerank(any(), any());
    }

    @Test
    @DisplayName("retrieve should delegate final ordering to rerank service for all retrieval requests")
    void retrieve_shouldDelegateFinalOrderingToRerankServiceForAllRetrievalRequests() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                denseDoc(
                        "redis-null-cache-expire-001",
                        "缓存空对象应该怎么设置过期时间",
                        "考察空对象缓存的 TTL 和一致性权衡",
                        "空对象缓存不能永久保留，需要结合 TTL 和脏数据窗口做权衡。",
                        List.of("TTL", "脏数据窗口"),
                        List.of("redis-null-cache-dirty-window-001"),
                        "redis",
                        "PRINCIPLE",
                        "L3",
                        List.of("Redis", "空对象缓存", "TTL")
                ),
                denseDoc(
                        "redis-cache-penetration-001",
                        "讲一下 Redis 缓存穿透",
                        "考察空对象缓存和布隆过滤器",
                        "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底，并关注误判影响。",
                        List.of("空对象缓存", "布隆过滤器", "误判"),
                        List.of("redis-bloom-false-positive-001", "redis-null-cache-expire-001"),
                        "redis",
                        "PRINCIPLE",
                        "L2",
                        List.of("Redis", "缓存穿透", "布隆过滤器")
                )
        ));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(Futures.immediateFuture(
                Points.ScrollResponse.newBuilder()
                        .addResult(point(
                                101L,
                                "redis-cache-penetration-001",
                                "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器",
                                "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底，并关注误判影响。",
                                List.of("空对象缓存", "布隆过滤器", "误判"),
                                List.of("把缓存穿透和击穿混淆"),
                                List.of("redis-bloom-false-positive-001", "redis-null-cache-expire-001"),
                                "redis",
                                "PRINCIPLE",
                                "L2",
                                List.of("Redis", "缓存穿透", "布隆过滤器")
                        ))
                        .addResult(point(
                                102L,
                                "redis-null-cache-expire-001",
                                "缓存空对象应该怎么设置过期时间",
                                "考察空对象缓存的 TTL 和一致性权衡",
                                "空对象缓存不能永久保留，需要结合 TTL 和脏数据窗口做权衡。",
                                List.of("TTL", "脏数据窗口"),
                                List.of("TTL 一刀切"),
                                List.of("redis-null-cache-dirty-window-001"),
                                "redis",
                                "PRINCIPLE",
                                "L3",
                                List.of("Redis", "空对象缓存", "TTL")
                        ))
                        .build()
        ));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("redis-cache-penetration-001", 0.98d),
                new RerankResult("redis-null-cache-expire-001", 0.12d)
        ));

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透 兜底方案 空对象缓存 布隆过滤器 误判")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("Redis 缓存穿透")
                .build();

        RagContext context = service.retrieve(request);

        ArgumentCaptor<List<RerankCandidate>> rerankCandidatesCaptor = ArgumentCaptor.forClass(List.class);
        verify(rerankService).rerank(eq(request), rerankCandidatesCaptor.capture());
        assertThat(rerankCandidatesCaptor.getValue()).extracting(RerankCandidate::questionId)
                .containsExactly("redis-null-cache-expire-001", "redis-cache-penetration-001");
        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001");
        assertThat(context.getRetrievalAudit()).isNotNull();
        assertThat(context.getRetrievalAudit().isRetrievalTriggered()).isTrue();
        assertThat(context.getRetrievalAudit().getLexicalCandidateCount()).isEqualTo(2);
        assertThat(context.getRetrievalAudit().getDenseCandidateCount()).isEqualTo(2);
        assertThat(context.getRetrievalAudit().getRerankPreTopQuestionIds())
                .containsExactly("redis-null-cache-expire-001", "redis-cache-penetration-001");
        assertThat(context.getRetrievalAudit().getRerankPostTopQuestionIds())
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001");
        assertThat(context.getRetrievalAudit().getInjectedQuestionIds())
                .containsExactly("redis-cache-penetration-001", "redis-null-cache-expire-001");
    }

    @Test
    @DisplayName("retrieve should fall back to dense order instead of clue contains ranking when rerank fails")
    void retrieve_shouldFallBackToDenseOrderInsteadOfClueContainsRankingWhenRerankFails() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                denseDoc(
                        "redis-cache-penetration-001",
                        "讲一下 Redis 缓存穿透",
                        "考察空对象缓存和布隆过滤器",
                        "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                        List.of("空对象缓存", "布隆过滤器"),
                        List.of("redis-bloom-false-positive-001"),
                        "redis",
                        "PRINCIPLE",
                        "L2",
                        List.of("Redis", "缓存穿透")
                ),
                denseDoc(
                        "redis-cache-avalanche-001",
                        "讲一下 Redis 缓存雪崩",
                        "考察大量 key 同时失效时的保护方案",
                        "要做 TTL 打散和限流兜底，同时这个题卡故意包含 must/avoid 字样。",
                        List.of("解释缓存穿透场景", "把缓存穿透和击穿混淆"),
                        List.of("redis-avalanche-follow-001"),
                        "redis",
                        "PRINCIPLE",
                        "L2",
                        List.of("Redis", "缓存雪崩")
                )
        ));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(Futures.immediateFuture(
                Points.ScrollResponse.newBuilder()
                        .addResult(point(
                                101L,
                                "redis-cache-penetration-001",
                                "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器",
                                "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"),
                                List.of("把缓存穿透和击穿混淆"),
                                List.of("redis-bloom-false-positive-001"),
                                "redis",
                                "PRINCIPLE",
                                "L2",
                                List.of("Redis", "缓存穿透")
                        ))
                        .addResult(point(
                                102L,
                                "redis-cache-avalanche-001",
                                "讲一下 Redis 缓存雪崩",
                                "考察大量 key 同时失效时的保护方案",
                                "要做 TTL 打散和限流兜底，同时这个题卡故意包含 must/avoid 字样。",
                                List.of("解释缓存穿透场景", "把缓存穿透和击穿混淆"),
                                List.of("把缓存穿透和击穿混淆"),
                                List.of("redis-avalanche-follow-001"),
                                "redis",
                                "PRINCIPLE",
                                "L2",
                                List.of("Redis", "缓存雪崩")
                        ))
                        .build()
        ));
        when(rerankService.rerank(any(), any())).thenThrow(new IllegalStateException("rerank down"));

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("Redis 缓存穿透 兜底方案 空对象缓存 布隆过滤器")
                .keywordQueries(List.of("Redis", "缓存穿透"))
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .focusPoint("Redis 缓存穿透")
                .build();

        RagContext context = service.retrieve(request);

        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("redis-cache-penetration-001", "redis-cache-avalanche-001");
    }

    @Test
    @DisplayName("retrieve should drop structurally polluting technical cards for behavioral questions")
    void retrieve_shouldDropStructurallyPollutingTechnicalCardsForBehavioralQuestions() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                denseDoc(
                        "behavior-conflict-001",
                        "讲一次你和产品意见不一致的经历",
                        "考察冲突沟通和推进结果",
                        "重点看你如何推动决策和复盘。",
                        List.of("个人动作", "推进过程", "结果复盘"),
                        List.of("behavior-push-hard-problem-001"),
                        "",
                        "BEHAVIORAL",
                        "L2",
                        List.of("沟通", "推进", "冲突")
                ),
                denseDoc(
                        "redis-cache-penetration-001",
                        "讲一下 Redis 缓存穿透",
                        "考察空对象缓存和布隆过滤器",
                        "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                        List.of("空对象缓存", "布隆过滤器"),
                        List.of("redis-bloom-false-positive-001"),
                        "redis",
                        "PRINCIPLE",
                        "L2",
                        List.of("Redis", "缓存穿透")
                )
        ));
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(Futures.immediateFuture(
                Points.ScrollResponse.newBuilder()
                        .addResult(point(
                                101L,
                                "behavior-conflict-001",
                                "讲一次你和产品意见不一致的经历",
                                "考察冲突沟通和推进结果",
                                "重点看你如何推动决策和复盘。",
                                List.of("个人动作", "推进过程", "结果复盘"),
                                List.of("空泛价值观表态"),
                                List.of("behavior-push-hard-problem-001"),
                                "",
                                "BEHAVIORAL",
                                "L2",
                                List.of("沟通", "推进", "冲突")
                        ))
                        .addResult(point(
                                102L,
                                "redis-cache-penetration-001",
                                "讲一下 Redis 缓存穿透",
                                "考察空对象缓存和布隆过滤器",
                                "高并发查询不存在数据时需要空对象缓存和布隆过滤器兜底。",
                                List.of("空对象缓存", "布隆过滤器"),
                                List.of("把缓存穿透和击穿混淆"),
                                List.of("redis-bloom-false-positive-001"),
                                "redis",
                                "PRINCIPLE",
                                "L2",
                                List.of("Redis", "缓存穿透")
                        ))
                        .build()
        ));
        when(rerankService.rerank(any(), any())).thenReturn(List.of(
                new RerankResult("redis-cache-penetration-001", 0.99d),
                new RerankResult("behavior-conflict-001", 0.50d)
        ));

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("行为面试 与产品意见不一致 冲突沟通 推进结果 复盘")
                .keywordQueries(List.of("沟通", "推进", "冲突", "协作"))
                .domainCode("")
                .questionType("BEHAVIORAL")
                .difficultyHint("L2")
                .focusPoint("与产品意见不一致")
                .build();

        RagContext context = service.retrieve(request);

        assertThat(context.getRetrievedMaterials()).extracting(RagContext.RetrievedMaterial::getQuestionId)
                .containsExactly("behavior-conflict-001");
    }

    @Test
    @DisplayName("retrieve should skip lexical prefilter when keyword queries are empty")
    void retrieve_shouldSkipLexicalPrefilterWhenKeywordQueriesAreEmpty() {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRerankService rerankService = mock(RagRerankService.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient, rerankService);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .queryText("行为面试 与产品意见不一致 冲突沟通 推进结果 复盘")
                .keywordQueries(List.of())
                .questionType("BEHAVIORAL")
                .domainCode("")
                .focusPoint("与产品意见不一致")
                .build();

        RagContext context = service.retrieve(request);

        assertThat(context.isEmpty()).isTrue();
        verify(qdrantClient, never()).scrollAsync(any());
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
        verify(rerankService, never()).rerank(any(), any());
    }

    private RagRetrievalServiceImpl newService(VectorStore vectorStore,
                                               QdrantClient qdrantClient,
                                               RagRerankService rerankService) {
        RagProperties properties = new RagProperties();
        properties.setTopK(3);
        properties.setMinScore(0.65);
        properties.setCollectionName("interview_knowledge");
        return new RagRetrievalServiceImpl(vectorStore, qdrantClient, rerankService, properties);
    }

    private Document denseDoc(String questionId,
                              String questionText,
                              String intentConcept,
                              String referenceContext,
                              List<String> scoringKeyPoints,
                              List<String> followUpIds,
                              String domainCode,
                              String questionType,
                              String difficulty,
                              List<String> keywords) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("question_id", questionId);
        metadata.put("question_text", questionText);
        metadata.put("intent_concept", intentConcept);
        metadata.put("reference_context", referenceContext);
        metadata.put("scoring_key_points", scoringKeyPoints);
        metadata.put("scoring_pitfalls", List.of());
        metadata.put("follow_up_ids", followUpIds);
        metadata.put("domain_code", domainCode);
        metadata.put("question_type", questionType);
        metadata.put("difficulty", difficulty);
        metadata.put("keywords", keywords);
        metadata.put("active", true);
        return new Document(
                String.join("\n", List.of(questionText, intentConcept, referenceContext)),
                metadata
        );
    }

    private Points.RetrievedPoint point(long pointId,
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
                                        List<String> keywords) {
        return Points.RetrievedPoint.newBuilder()
                .setId(PointIdFactory.id(pointId))
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
                .putPayload("doc_content", ValueFactory.value(String.join("\n", List.of(questionText, intentConcept, referenceContext))))
                .build();
    }

    private List<String> filterMatches(Points.Filter filter, String fieldKey) {
        if (filter == null) {
            return List.of();
        }
        return filter.getMustList().stream()
                .filter(Points.Condition::hasField)
                .map(Points.Condition::getField)
                .filter(field -> fieldKey.equals(field.getKey()) && field.hasMatch())
                .map(field -> field.getMatch().getKeyword())
                .toList();
    }

    private List<String> minShouldTextMatches(Points.Filter filter, String fieldKey) {
        if (filter == null || !filter.hasMinShould()) {
            return List.of();
        }
        return filter.getMinShould().getConditionsList().stream()
                .filter(Points.Condition::hasField)
                .map(Points.Condition::getField)
                .filter(field -> fieldKey.equals(field.getKey()) && field.hasMatch() && !field.getMatch().getText().isBlank())
                .map(field -> field.getMatch().getText())
                .toList();
    }

    private List<String> minShouldKeywordMatches(Points.Filter filter, String fieldKey) {
        if (filter == null || !filter.hasMinShould()) {
            return List.of();
        }
        return filter.getMinShould().getConditionsList().stream()
                .filter(Points.Condition::hasField)
                .map(Points.Condition::getField)
                .filter(field -> fieldKey.equals(field.getKey()) && field.hasMatch())
                .map(field -> field.getMatch().getKeyword())
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .toList();
    }
}
