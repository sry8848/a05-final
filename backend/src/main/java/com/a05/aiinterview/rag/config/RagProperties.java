package com.a05.aiinterview.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 模块全局配置属性。
 *
 * <p>通过 {@code rag.*} 配置前缀注入，控制 Qdrant 连接参数、检索行为及样本数据初始化开关。
 * 当 {@code rag.enabled=false}（默认）时，RAG 检索链路完全旁路，出题行为与接入前完全一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** 是否启用 RAG 检索，默认 false（向后兼容） */
    private boolean enabled = false;

    /** 向量检索最多返回的文档片段数 */
    private int topK = 5;

    /** 相似度最低阈值，低于此分数的结果被过滤（0~1） */
    private double minScore = 0.65;

    /** Qdrant gRPC 服务地址（Spring AI 通过 gRPC port 6334 与 Qdrant 交互） */
    private String host = "localhost";

    /** Qdrant gRPC 端口，默认 6334 */
    private int port = 6334;

    /** Qdrant 集合名称 */
    private String collectionName = "interview_knowledge";

    /** 若集合不存在是否自动创建（生产环境建议手动预创建后设为 false） */
    private boolean initializeSchema = true;

    /** 启动时是否自动注入内置样本知识数据（仅用于开发/演示，生产应关闭） */
    private boolean initSampleData = false;

    /** 商业化 rerank 配置。 */
    private RerankProperties rerank = new RerankProperties();

    @Data
    public static class RerankProperties {
        /** 百炼文本排序 API Key，默认复用 AI_BAILIAN_API_KEY / OPENAI_API_KEY。 */
        private String apiKey = "";

        /** 百炼文本排序 API endpoint。 */
        private String endpoint = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        /** 默认使用的 rerank 模型。 */
        private String model = "gte-rerank-v2";

        /** rerank 网络超时。 */
        private int timeoutMs = 5000;

        /** rerank 最大候选规模。 */
        private int topN = 10;
    }
}
