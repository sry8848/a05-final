package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagContext;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 检索服务真实实现。
 *
 * <p>仅当 {@code rag.enabled=true} 时激活，依赖 {@link RagConfiguration} 提供的 {@link VectorStore} Bean。
 *
 * <p>检索策略：
 * <ol>
 *   <li>以 focusPoint + domainCode + questionType + difficultyHint 拼接查询文本</li>
 *   <li>按 {@code domain_code} 进行 metadata 精确过滤，降低跨域噪声</li>
 *   <li>按 score 降序排列，取 top-k 结果</li>
 *   <li>聚合为可注入 Prompt 的段落文本</li>
 *   <li>任何异常均降级返回 {@link RagContext#empty()}，不阻断主链路</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    @Override
    public RagContext retrieve(RagRetrievalRequest request) {
        log.info("RAG 检索开始, domainCode={}, questionType={}, difficultyHint={}, focusPoint={}",
                request.getDomainCode(), request.getQuestionType(),
                request.getDifficultyHint(), request.getFocusPoint());
        try {
            String queryText = buildQueryText(request);
            SearchRequest searchRequest = buildSearchRequest(queryText, request);

            List<Document> docs = vectorStore.similaritySearch(searchRequest);

            if (docs == null || docs.isEmpty()) {
                log.info("RAG 检索无命中, domainCode={}", request.getDomainCode());
                return RagContext.empty();
            }

            String contextText = aggregateContext(docs);
            log.info("RAG 检索完成, 命中片段数={}, domainCode={}", docs.size(), request.getDomainCode());

            return RagContext.builder()
                    .contextText(contextText)
                    .hitCount(docs.size())
                    .empty(false)
                    .build();

        } catch (Exception e) {
            // 检索异常必须降级，不阻断出题主链路
            log.error("RAG 检索异常，降级返回空上下文, domainCode={}, questionType={}",
                    request.getDomainCode(), request.getQuestionType(), e);
            return RagContext.empty();
        }
    }

    /**
     * 构建向量检索查询文本。
     * 将多个检索维度融合为一段自然语言，充分利用语义向量的表达能力。
     */
    private String buildQueryText(RagRetrievalRequest request) {
        StringBuilder sb = new StringBuilder();

        // focusPoint 是最核心的语义信息，放在最前
        if (request.getFocusPoint() != null && !request.getFocusPoint().isBlank()) {
            sb.append(request.getFocusPoint());
        }
        if (request.getDomainCode() != null && !request.getDomainCode().isBlank()) {
            sb.append(" ").append(request.getDomainCode());
        }
        if (request.getQuestionType() != null && !request.getQuestionType().isBlank()) {
            sb.append(" ").append(request.getQuestionType());
        }
        if (request.getDifficultyHint() != null && !request.getDifficultyHint().isBlank()) {
            sb.append(" 深度 ").append(request.getDifficultyHint());
        }

        String query = sb.toString().trim();
        // 若所有维度均为空，使用 domainCode 兜底，避免空查询
        return query.isBlank() ? (request.getDomainCode() != null ? request.getDomainCode() : "面试知识") : query;
    }

    /**
     * 构建 SearchRequest，优先按 domain_code 做 metadata 过滤，降低跨域噪声。
     * difficultyHint 只参与 query 文本，不参与 metadata 等值硬过滤。
     * 过滤表达式使用 Spring AI 可移植的文本 DSL，Qdrant 会自动转为原生过滤器。
     */
    private SearchRequest buildSearchRequest(String queryText, RagRetrievalRequest request) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(queryText)
                .topK(ragProperties.getTopK())
                .similarityThreshold(ragProperties.getMinScore());

        // 按知识域过滤，避免召回无关领域的片段
        if (request.getDomainCode() != null && !request.getDomainCode().isBlank()) {
            builder.filterExpression("domain_code == '" + request.getDomainCode() + "'");
        }

        return builder.build();
    }

    /**
     * 将检索到的文档片段聚合为可注入 Prompt 的段落文本。
     * 每个片段添加序号标题，便于模型感知片段边界。
     */
    private String aggregateContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            sb.append("--- 参考资料 ").append(i + 1).append(" ---\n");
            // Spring AI 1.0.0 Document 通过 getText() 获取文本内容
            sb.append(docs.get(i).getText()).append("\n\n");
        }
        return sb.toString().trim();
    }
}
