package com.a05.aiinterview.rag.qdrant;

import com.a05.aiinterview.rag.config.RagProperties;
import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("QdrantHybridCollectionManager tests")
class QdrantHybridCollectionManagerTest {

    @Test
    @DisplayName("ensureCollectionSchema should create hybrid collection with named dense and sparse vectors when collection is absent")
    void ensureCollectionSchema_shouldCreateHybridCollectionWhenCollectionIsAbsent() throws Exception {
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagProperties properties = new RagProperties();
        properties.setCollectionName("interview_knowledge_hybrid");
        properties.setDenseVectorName("dense");
        properties.setSparseVectorName("bm25");

        when(qdrantClient.collectionExistsAsync("interview_knowledge_hybrid"))
                .thenReturn(Futures.immediateFuture(false));
        when(qdrantClient.createCollectionAsync(any(Collections.CreateCollection.class)))
                .thenReturn(Futures.immediateFuture(Collections.CollectionOperationResponse.newBuilder().build()));
        when(qdrantClient.createPayloadIndexAsync(
                eq("interview_knowledge_hybrid"),
                any(String.class),
                any(Collections.PayloadSchemaType.class),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        )).thenReturn(Futures.immediateFuture(Points.UpdateResult.newBuilder().build()));

        QdrantHybridCollectionManager manager = new QdrantHybridCollectionManager(qdrantClient, properties);
        manager.ensureCollectionSchema();

        ArgumentCaptor<Collections.CreateCollection> captor = ArgumentCaptor.forClass(Collections.CreateCollection.class);
        verify(qdrantClient).createCollectionAsync(captor.capture());

        Collections.CreateCollection createCollection = captor.getValue();
        assertThat(createCollection.getCollectionName()).isEqualTo("interview_knowledge_hybrid");
        assertThat(createCollection.hasVectorsConfig()).isTrue();
        assertThat(createCollection.getVectorsConfig().hasParamsMap()).isTrue();
        assertThat(createCollection.getVectorsConfig().getParamsMap().containsMap("dense")).isTrue();
        assertThat(createCollection.getSparseVectorsConfig().containsMap("bm25")).isTrue();
        assertThat(createCollection.getSparseVectorsConfig().getMapOrThrow("bm25").getModifier())
                .isEqualTo(Collections.Modifier.Idf);
    }

    @Test
    @DisplayName("ensureCollectionSchema should register only metadata payload indexes required by hybrid retrieval")
    void ensureCollectionSchema_shouldRegisterOnlyMetadataPayloadIndexesRequiredByHybridRetrieval() throws Exception {
        QdrantClient qdrantClient = mock(QdrantClient.class);
        RagProperties properties = new RagProperties();
        properties.setCollectionName("interview_knowledge_hybrid");

        when(qdrantClient.collectionExistsAsync("interview_knowledge_hybrid"))
                .thenReturn(Futures.immediateFuture(true));
        when(qdrantClient.createPayloadIndexAsync(
                eq("interview_knowledge_hybrid"),
                any(String.class),
                any(Collections.PayloadSchemaType.class),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        )).thenReturn(Futures.immediateFuture(Points.UpdateResult.newBuilder().build()));

        QdrantHybridCollectionManager manager = new QdrantHybridCollectionManager(qdrantClient, properties);
        manager.ensureCollectionSchema();

        verify(qdrantClient, times(1)).createPayloadIndexAsync(
                eq("interview_knowledge_hybrid"),
                eq("question_type"),
                eq(Collections.PayloadSchemaType.Keyword),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        );
        verify(qdrantClient, times(1)).createPayloadIndexAsync(
                eq("interview_knowledge_hybrid"),
                eq("domain_code"),
                eq(Collections.PayloadSchemaType.Keyword),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        );
        verify(qdrantClient, times(1)).createPayloadIndexAsync(
                eq("interview_knowledge_hybrid"),
                eq("active"),
                eq(Collections.PayloadSchemaType.Bool),
                any(Collections.PayloadIndexParams.class),
                eq(Boolean.TRUE),
                eq(Points.WriteOrderingType.Weak),
                any(Duration.class)
        );
    }
}
