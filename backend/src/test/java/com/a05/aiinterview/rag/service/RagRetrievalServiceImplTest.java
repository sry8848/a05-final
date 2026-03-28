package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RagRetrievalServiceImpl tests")
class RagRetrievalServiceImplTest {

    @Test
    @DisplayName("retrieve should run dense and sparse recall, fuse with RRF, and rerank with mustHave/avoid clues")
    void retrieve_shouldRunDenseAndSparseRecallFuseWithRrfAndRerankByBusinessSignals() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient);

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

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .displayQuery("Redis缓存穿透")
                .queryText("Redis 缓存穿透 兜底方案 空对象缓存 布隆过滤器 误判")
                .keywordQueries(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficultyHint("L2")
                .mustHaveClues(List.of("空对象缓存", "布隆过滤器"))
                .avoidClues(List.of("部署教程", "基础 API"))
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

        assertThat(context.isEmpty()).isFalse();
        assertThat(context.getRetrievedMaterials()).hasSize(3);
        assertThat(context.getRetrievedMaterials().getFirst().getQuestionId()).isEqualTo("redis-cache-penetration-001");
        assertThat(context.getRetrievedMaterials().getFirst().getQuestionText()).contains("缓存穿透");
        assertThat(context.getSummary()).contains("Redis 缓存穿透");
        assertThat(context.getFollowUpCandidates())
                .contains("redis-bloom-false-positive-001", "redis-null-cache-expire-001")
                .doesNotContain("redis-install-follow-001");
        assertThat(context.getRetrievedMaterials().stream().map(RagContext.RetrievedMaterial::getQuestionId))
                .contains("redis-null-cache-expire-001");
    }

    @Test
    @DisplayName("retrieve should allow behavioral retrieval without domainCode filter")
    void retrieve_shouldAllowBehavioralRetrievalWithoutDomainCodeFilter() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient);

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(qdrantClient.scrollAsync(any(Points.ScrollPoints.class))).thenReturn(Futures.immediateFuture(
                Points.ScrollResponse.newBuilder().build()
        ));

        RagRetrievalRequest request = RagRetrievalRequest.builder()
                .shouldRetrieve(true)
                .displayQuery("与产品意见不一致")
                .queryText("行为面试 与产品意见不一致 冲突沟通 推进结果 复盘")
                .keywordQueries(List.of("沟通", "推进", "冲突", "协作"))
                .domainCode("")
                .questionType("BEHAVIORAL")
                .difficultyHint("L2")
                .mustHaveClues(List.of("沟通动作", "推进过程"))
                .avoidClues(List.of("技术原理"))
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
    }

    @Test
    @DisplayName("retrieve should short circuit when shouldRetrieve is false")
    void retrieve_shouldShortCircuitWhenShouldRetrieveIsFalse() {
        VectorStore vectorStore = mock(VectorStore.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagRetrievalServiceImpl service = newService(vectorStore, qdrantClient);

        RagContext context = service.retrieve(RagRetrievalRequest.builder()
                .shouldRetrieve(false)
                .displayQuery("泛项目叙述")
                .build());

        assertThat(context.isEmpty()).isTrue();
        assertThat(context.getRetrievedMaterials()).isEmpty();
        assertThat(context.getFollowUpCandidates()).isEmpty();
    }

    private RagRetrievalServiceImpl newService(VectorStore vectorStore, QdrantClient qdrantClient) {
        RagProperties properties = new RagProperties();
        properties.setTopK(3);
        properties.setMinScore(0.65);
        properties.setCollectionName("interview_knowledge");
        return new RagRetrievalServiceImpl(vectorStore, qdrantClient, properties);
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
}
