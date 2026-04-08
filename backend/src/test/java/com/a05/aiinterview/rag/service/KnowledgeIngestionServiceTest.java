package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.config.RagProperties;
import com.google.common.util.concurrent.Futures;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@DisplayName("KnowledgeIngestionService tests")
class KnowledgeIngestionServiceTest {

    @Test
    @DisplayName("ingest should upsert hybrid point with dense vector sparse document and metadata")
    void ingest_shouldUpsertHybridPointWithDenseVectorSparseDocumentAndMetadata() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagProperties properties = new RagProperties();
        KnowledgeIngestionService service = new KnowledgeIngestionService(embeddingModel, qdrantClient, properties);

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id("redis-cache-penetration-001")
                .questionText("讲一下 Redis 缓存穿透")
                .intentConcept("考察空值缓存、布隆过滤器和数据库保护方案")
                .referenceContext("高并发查询不存在数据时，缓存层需要做兜底，避免数据库被持续打穿。")
                .scoringKeyPoints(List.of("缓存空对象", "布隆过滤器", "方案局限性"))
                .scoringPitfalls(List.of("混淆穿透和击穿"))
                .followUpIds(List.of("redis-bloom-filter-false-positive-001"))
                .domainCode("redis")
                .questionType("PRINCIPLE")
                .difficulty("L2")
                .keywords(List.of("Redis", "缓存穿透", "布隆过滤器"))
                .source("manual_curated")
                .active(true)
                .version("v1")
                .build();

        when(embeddingModel.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(qdrantClient.upsertAsync(any(String.class), any(List.class)))
                .thenReturn(Futures.immediateFuture(Points.UpdateResult.newBuilder().build()));

        int written = service.ingest(List.of(document));

        assertThat(written).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Points.PointStruct>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient).upsertAsync(any(String.class), captor.capture());

        List<Points.PointStruct> storedPoints = captor.getValue();
        assertThat(storedPoints).hasSize(1);

        Points.PointStruct stored = storedPoints.getFirst();
        assertThat(stored.getId())
                .isEqualTo(PointIdFactory.id(UUID.nameUUIDFromBytes("redis-cache-penetration-001".getBytes())));
        assertThat(stored.getVectors().getVectors().containsVectors("dense")).isTrue();
        assertThat(stored.getVectors().getVectors().getVectorsOrThrow("dense").getDataList())
                .containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(stored.getVectors().getVectors().containsVectors("bm25")).isTrue();
        assertThat(stored.getVectors().getVectors().getVectorsOrThrow("bm25").hasDocument()).isTrue();
        assertThat(stored.getVectors().getVectors().getVectorsOrThrow("bm25").getDocument().getModel()).isEqualTo("qdrant/bm25");
        assertThat(stored.getVectors().getVectors().getVectorsOrThrow("bm25").getDocument().getText())
                .contains("讲一下 Redis 缓存穿透")
                .contains("Redis")
                .contains("缓存穿透")
                .contains("布隆过滤器")
                .doesNotContain("高并发查询不存在数据时");

        assertThat(stored.getPayloadMap())
                .containsKeys("question_id", "question_text", "intent_concept", "reference_context",
                        "domain_code", "question_type", "difficulty", "source", "active", "version",
                        "keywords", "scoring_key_points", "scoring_pitfalls", "follow_up_ids");
    }

    @Test
    @DisplayName("ingest should use stable uuid point ids derived from question id")
    void ingest_shouldUseStableUuidPointIdsDerivedFromQuestionId() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        when(embeddingModel.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.upsertAsync(any(String.class), any(List.class)))
                .thenReturn(Futures.immediateFuture(Points.UpdateResult.newBuilder().build()));
        KnowledgeIngestionService service = new KnowledgeIngestionService(embeddingModel, qdrantClient, new RagProperties());

        KnowledgeDocument first = KnowledgeDocument.builder()
                .id("redis-cache-penetration-001")
                .questionText("讲一下 Redis 缓存穿透")
                .intentConcept("考察空值缓存、布隆过滤器和数据库保护方案")
                .referenceContext("第一次入库内容")
                .questionType("PRINCIPLE")
                .difficulty("L2")
                .source("manual_curated")
                .active(true)
                .version("v1")
                .build();

        KnowledgeDocument second = KnowledgeDocument.builder()
                .id("redis-cache-penetration-001")
                .questionText("讲一下 Redis 缓存穿透")
                .intentConcept("考察空值缓存、布隆过滤器和数据库保护方案")
                .referenceContext("第二次入库内容")
                .questionType("PRINCIPLE")
                .difficulty("L2")
                .source("manual_curated")
                .active(true)
                .version("v2")
                .build();

        service.ingest(List.of(first));
        service.ingest(List.of(second));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Points.PointStruct>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient, times(2)).upsertAsync(any(String.class), captor.capture());

        List<List<Points.PointStruct>> allBatches = captor.getAllValues();
        Points.PointId expectedId = PointIdFactory.id(UUID.nameUUIDFromBytes("redis-cache-penetration-001".getBytes()));
        assertThat(allBatches.get(0).getFirst().getId()).isEqualTo(expectedId);
        assertThat(allBatches.get(1).getFirst().getId()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("ingest should split large batches to satisfy explicit hybrid upsert limits")
    void ingest_shouldSplitLargeBatchesToSatisfyExplicitHybridUpsertLimits() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        when(embeddingModel.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f});
        when(qdrantClient.upsertAsync(any(String.class), any(List.class)))
                .thenReturn(Futures.immediateFuture(Points.UpdateResult.newBuilder().build()));
        KnowledgeIngestionService service = new KnowledgeIngestionService(embeddingModel, qdrantClient, new RagProperties());

        List<KnowledgeDocument> documents = IntStream.range(0, 11)
                .mapToObj(index -> KnowledgeDocument.builder()
                        .id("doc-" + index)
                        .questionText("题目 " + index)
                        .intentConcept("考点 " + index)
                        .referenceContext("语境 " + index)
                        .scoringKeyPoints(List.of("关键点 " + index))
                        .scoringPitfalls(List.of("误区 " + index))
                        .followUpIds(List.of("follow-" + index))
                        .domainCode("redis")
                        .questionType("PRINCIPLE")
                        .difficulty("L2")
                        .keywords(List.of("Redis", "缓存"))
                        .source("manual_curated")
                        .active(true)
                        .version("v1")
                        .build())
                .toList();

        service.ingest(documents);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Points.PointStruct>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient, times(2)).upsertAsync(any(String.class), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(List::size)
                .containsExactly(10, 1);
    }
}
