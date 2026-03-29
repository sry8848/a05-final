package com.a05.aiinterview.rag.config;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

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

    @Bean
    public SmartInitializingSingleton ragLexicalIndexInitializer(VectorStore vectorStore,
                                                                 QdrantClient qdrantClient,
                                                                 RagProperties ragProperties) {
        return () -> {
            try {
                ensureLexicalPayloadIndexes(qdrantClient, ragProperties);
            } catch (Exception e) {
                throw new IllegalStateException("初始化 Qdrant lexical payload indexes 失败", e);
            }
        };
    }

    void ensureLexicalPayloadIndexes(QdrantClient qdrantClient, RagProperties ragProperties) throws Exception {
        String collectionName = ragProperties.getCollectionName();
        log.info("初始化 Qdrant lexical payload indexes, collection={}", collectionName);

        qdrantClient.createPayloadIndexAsync(
                collectionName,
                "question_text",
                Collections.PayloadSchemaType.Text,
                lexicalTextIndexParams(),
                true,
                Points.WriteOrderingType.Weak,
                Duration.ofSeconds(5)
        ).get(5, TimeUnit.SECONDS);

        qdrantClient.createPayloadIndexAsync(
                collectionName,
                "intent_concept",
                Collections.PayloadSchemaType.Text,
                lexicalTextIndexParams(),
                true,
                Points.WriteOrderingType.Weak,
                Duration.ofSeconds(5)
        ).get(5, TimeUnit.SECONDS);

        qdrantClient.createPayloadIndexAsync(
                collectionName,
                "keywords",
                Collections.PayloadSchemaType.Keyword,
                Collections.PayloadIndexParams.newBuilder()
                        .setKeywordIndexParams(Collections.KeywordIndexParams.newBuilder().build())
                        .build(),
                true,
                Points.WriteOrderingType.Weak,
                Duration.ofSeconds(5)
        ).get(5, TimeUnit.SECONDS);
    }

    Collections.PayloadIndexParams lexicalTextIndexParams() {
        return Collections.PayloadIndexParams.newBuilder()
                .setTextIndexParams(Collections.TextIndexParams.newBuilder()
                        .setTokenizer(Collections.TokenizerType.Multilingual)
                        .setLowercase(true)
                        .build())
                .build();
    }
}
