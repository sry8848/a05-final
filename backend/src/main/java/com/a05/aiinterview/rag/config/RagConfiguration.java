package com.a05.aiinterview.rag.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 基础设施条件配置。
 *
 * <p>仅当 {@code rag.enabled=true} 时激活，负责创建 Qdrant 客户端与 VectorStore Bean。
 * application.yml 中已通过 {@code spring.autoconfigure.exclude} 排除了 Qdrant 自动配置，
 * 因此本类是唯一的 Qdrant Bean 来源，可安全地用条件注解控制其生命周期。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagConfiguration {

    /**
     * 创建 Qdrant gRPC 客户端。
     *
     * @param ragProperties RAG 配置（host/port 参数）
     * @return QdrantClient 实例
     */
    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(RagProperties ragProperties) {
        log.info("初始化 Qdrant 客户端, host={}, port={}", ragProperties.getHost(), ragProperties.getPort());
        QdrantGrpcClient grpcClient = QdrantGrpcClient
                .newBuilder(ragProperties.getHost(), ragProperties.getPort(), false)
                .build();
        return new QdrantClient(grpcClient);
    }

    /**
     * 创建 Spring AI VectorStore（Qdrant 实现）。
     *
     * @param qdrantClient   Qdrant 客户端
     * @param embeddingModel 向量化模型（由 spring-ai-starter-model-openai 自动注入）
     * @param ragProperties  RAG 配置（集合名等参数）
     * @return VectorStore 实例
     */
    @Bean
    public VectorStore vectorStore(QdrantClient qdrantClient,
                                   EmbeddingModel embeddingModel,
                                   RagProperties ragProperties) {
        log.info("初始化 Qdrant VectorStore, collection={}, initSchema={}",
                ragProperties.getCollectionName(), ragProperties.isInitializeSchema());
        return QdrantVectorStore.builder(qdrantClient, embeddingModel)
                .collectionName(ragProperties.getCollectionName())
                .initializeSchema(ragProperties.isInitializeSchema())
                .build();
    }
}
