package com.a05.aiinterview.rag.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import com.a05.aiinterview.rag.qdrant.QdrantHybridCollectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 基础设施条件配置。
 *
 * <p>仅当 {@code rag.enabled=true} 时激活，负责创建 Qdrant 客户端与 hybrid collection 管理器。
 * 当前阶段检索与入库主路径基于 Qdrant 原生 Java Client，不再依赖 Spring AI 的旧存储抽象作为主职责。
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

    @Bean
    public QdrantHybridCollectionManager ragHybridCollectionManager(QdrantClient qdrantClient,
                                                                    RagProperties ragProperties) {
        return new QdrantHybridCollectionManager(qdrantClient, ragProperties);
    }

    @Bean
    public SmartInitializingSingleton ragHybridSchemaInitializer(QdrantHybridCollectionManager collectionManager,
                                                                 RagProperties ragProperties) {
        return () -> {
            if (!ragProperties.isInitializeSchema()) {
                log.info("跳过 Qdrant hybrid schema 初始化, collection={}", ragProperties.getCollectionName());
                return;
            }
            try {
                collectionManager.ensureCollectionSchema();
            } catch (Exception e) {
                throw new IllegalStateException("初始化 Qdrant hybrid collection schema 失败", e);
            }
        };
    }
}
