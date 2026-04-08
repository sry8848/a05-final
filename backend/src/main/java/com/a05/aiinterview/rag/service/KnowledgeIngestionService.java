package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import com.a05.aiinterview.rag.qdrant.QdrantHybridPointMapper;
import com.google.common.util.concurrent.ListenableFuture;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 知识文档入库服务。
 *
 * <p>负责将题目卡片映射为 Qdrant hybrid point，并通过原生 Qdrant Java Client 写入 named dense vector、
 * sparse/BM25 document 和 payload metadata。
 *
 * <p>仅当 {@code rag.enabled=true} 时激活，避免在未接入 Qdrant 时注入失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class KnowledgeIngestionService {

    private static final int MAX_EMBEDDING_BATCH_SIZE = 10;
    private static final int UPSERT_TIMEOUT_SECONDS = 10;

    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;
    private final QdrantHybridPointMapper pointMapper;

    public KnowledgeIngestionService(EmbeddingModel embeddingModel,
                                     QdrantClient qdrantClient,
                                     RagProperties ragProperties) {
        this(embeddingModel, qdrantClient, ragProperties, new QdrantHybridPointMapper());
    }

    /**
     * 批量入库知识文档列表。
     *
     * @param documents 待入库的题目卡片列表
     * @return 实际写入 Qdrant 的文档总数
     */
    public int ingest(List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("知识入库：文档列表为空，跳过");
            return 0;
        }

        log.info("知识入库开始, 文档数={}", documents.size());
        List<Points.PointStruct> points = new ArrayList<>();

        for (KnowledgeDocument doc : documents) {
            float[] denseVector = embeddingModel.embed(doc.toDenseRetrievalText());
            points.add(pointMapper.toPoint(doc, denseVector, ragProperties));
        }

        for (int start = 0; start < points.size(); start += MAX_EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + MAX_EMBEDDING_BATCH_SIZE, points.size());
            ListenableFuture<Points.UpdateResult> future = qdrantClient.upsertAsync(
                    ragProperties.getCollectionName(),
                    points.subList(start, end)
            );
            try {
                future.get(UPSERT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Qdrant hybrid upsert 失败", e);
            }
        }
        log.info("知识入库完成, 原始文档数={}, 写入条数={}", documents.size(), points.size());
        return points.size();
    }
}
