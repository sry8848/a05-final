package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@DisplayName("KnowledgeIngestionService tests")
class KnowledgeIngestionServiceTest {

    @Test
    @DisplayName("ingest should persist single-corpus interview card metadata and retrieval text")
    void ingest_shouldPersistSingleCorpusInterviewCardMetadataAndRetrievalText() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeIngestionService service = new KnowledgeIngestionService(vectorStore, new RagProperties());

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

        int written = service.ingest(List.of(document));

        assertThat(written).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> storedDocuments = captor.getValue();
        assertThat(storedDocuments).hasSize(1);

        Document stored = storedDocuments.getFirst();
        assertThat(stored.getText())
                .contains("讲一下 Redis 缓存穿透")
                .contains("考察空值缓存、布隆过滤器和数据库保护方案")
                .contains("高并发查询不存在数据时")
                .contains("缓存空对象")
                .contains("布隆过滤器")
                .doesNotContain("混淆穿透和击穿");

        Map<String, Object> metadata = stored.getMetadata();
        assertThat(metadata)
                .containsEntry("question_id", "redis-cache-penetration-001")
                .containsEntry("question_text", "讲一下 Redis 缓存穿透")
                .containsEntry("intent_concept", "考察空值缓存、布隆过滤器和数据库保护方案")
                .containsEntry("reference_context", "高并发查询不存在数据时，缓存层需要做兜底，避免数据库被持续打穿。")
                .containsEntry("domain_code", "redis")
                .containsEntry("question_type", "PRINCIPLE")
                .containsEntry("difficulty", "L2")
                .containsEntry("source", "manual_curated")
                .containsEntry("active", true)
                .containsEntry("version", "v1");
        assertThat(metadata.get("scoring_key_points")).isEqualTo(List.of("缓存空对象", "布隆过滤器", "方案局限性"));
        assertThat(metadata.get("scoring_pitfalls")).isEqualTo(List.of("混淆穿透和击穿"));
        assertThat(metadata.get("keywords")).isEqualTo(List.of("Redis", "缓存穿透", "布隆过滤器"));
        assertThat(metadata.get("follow_up_ids")).isEqualTo(List.of("redis-bloom-filter-false-positive-001"));
    }

    @Test
    @DisplayName("ingest should split large batches to satisfy embedding provider request limits")
    void ingest_shouldSplitLargeBatchesToSatisfyEmbeddingProviderRequestLimits() {
        VectorStore vectorStore = mock(VectorStore.class);
        KnowledgeIngestionService service = new KnowledgeIngestionService(vectorStore, new RagProperties());

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
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(List::size)
                .containsExactly(10, 1);
    }
}
