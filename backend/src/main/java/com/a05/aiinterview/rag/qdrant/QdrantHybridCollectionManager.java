package com.a05.aiinterview.rag.qdrant;

import com.a05.aiinterview.rag.config.RagProperties;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Qdrant hybrid collection schema 管理器。
 *
 * <p>负责新 collection 的 named dense vector、sparse vector 与必要 payload index 初始化。
 * 当前阶段仅承接 schema 管理职责；检索与入库主路径在后续 chunk 中逐步切到原生 Qdrant Java Client。
 */
@Slf4j
@RequiredArgsConstructor
public class QdrantHybridCollectionManager {

    private static final Duration COLLECTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PAYLOAD_INDEX_TIMEOUT = Duration.ofSeconds(5);

    private final QdrantClient qdrantClient;
    private final RagProperties ragProperties;

    public void ensureCollectionSchema() throws Exception {
        String collectionName = ragProperties.getCollectionName();
        Boolean exists = qdrantClient.collectionExistsAsync(collectionName)
                .get(COLLECTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        if (!Boolean.TRUE.equals(exists)) {
            log.info("创建 Qdrant hybrid collection, collection={}", collectionName);
            qdrantClient.createCollectionAsync(buildCreateCollection())
                    .get(COLLECTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } else {
            log.info("Qdrant hybrid collection 已存在, collection={}", collectionName);
        }

        ensureMetadataPayloadIndexes(collectionName);
    }

    private Collections.CreateCollection buildCreateCollection() {
        Collections.VectorParams denseVectorParams = Collections.VectorParams.newBuilder()
                .setSize(ragProperties.getDenseVectorSize())
                .setDistance(Collections.Distance.Cosine)
                .build();

        Collections.VectorParamsMap denseVectorMap = Collections.VectorParamsMap.newBuilder()
                .putMap(ragProperties.getDenseVectorName(), denseVectorParams)
                .build();

        Collections.SparseVectorParams sparseVectorParams = Collections.SparseVectorParams.newBuilder()
                .setIndex(Collections.SparseIndexConfig.newBuilder().build())
                .setModifier(Collections.Modifier.Idf)
                .build();

        Collections.SparseVectorConfig sparseVectorConfig = Collections.SparseVectorConfig.newBuilder()
                .putMap(ragProperties.getSparseVectorName(), sparseVectorParams)
                .build();

        return Collections.CreateCollection.newBuilder()
                .setCollectionName(ragProperties.getCollectionName())
                .setVectorsConfig(Collections.VectorsConfig.newBuilder()
                        .setParamsMap(denseVectorMap)
                        .build())
                .setSparseVectorsConfig(sparseVectorConfig)
                .build();
    }

    private void ensureMetadataPayloadIndexes(String collectionName) throws Exception {
        createKeywordPayloadIndex(collectionName, "question_type");
        createBooleanPayloadIndex(collectionName, "active");
    }

    private void createKeywordPayloadIndex(String collectionName, String fieldName) throws Exception {
        qdrantClient.createPayloadIndexAsync(
                collectionName,
                fieldName,
                Collections.PayloadSchemaType.Keyword,
                Collections.PayloadIndexParams.newBuilder()
                        .setKeywordIndexParams(Collections.KeywordIndexParams.newBuilder().build())
                        .build(),
                true,
                Points.WriteOrderingType.Weak,
                PAYLOAD_INDEX_TIMEOUT
        ).get(PAYLOAD_INDEX_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private void createBooleanPayloadIndex(String collectionName, String fieldName) throws Exception {
        qdrantClient.createPayloadIndexAsync(
                collectionName,
                fieldName,
                Collections.PayloadSchemaType.Bool,
                Collections.PayloadIndexParams.newBuilder()
                        .setBoolIndexParams(Collections.BoolIndexParams.newBuilder().build())
                        .build(),
                true,
                Points.WriteOrderingType.Weak,
                PAYLOAD_INDEX_TIMEOUT
        ).get(PAYLOAD_INDEX_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }
}
