package com.a05.aiinterview.rag.service;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识文档入库服务。
 *
 * <p>负责将单库题目卡片拼接为 retrieval_text、标注 metadata，并批量写入 Qdrant VectorStore。
 * 向量化（Embedding）由 Spring AI 的 {@link VectorStore#add} 内部调用 EmbeddingModel 完成，
 * 业务层无需感知 Embedding 细节。
 *
 * <p>仅当 {@code rag.enabled=true} 时激活，避免在未接入 Qdrant 时注入失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class KnowledgeIngestionService {

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

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
        List<Document> springAiDocs = new ArrayList<>();

        for (KnowledgeDocument doc : documents) {
            springAiDocs.add(new Document(doc.toRetrievalText(), buildMetadata(doc)));
        }

        vectorStore.add(springAiDocs);
        log.info("知识入库完成, 原始文档数={}, 写入条数={}", documents.size(), springAiDocs.size());
        return springAiDocs.size();
    }

    /**
     * 将 KnowledgeDocument 的字段映射为 Qdrant metadata 键值对。
     * metadata 字段是检索过滤和后续重排的核心依据。
     *
     * @param doc 知识文档
     * @return 可直接传入 Spring AI Document 的 metadata Map
     */
    private Map<String, Object> buildMetadata(KnowledgeDocument doc) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("question_id", nullSafe(doc.getId()));
        meta.put("domain_code", nullSafe(doc.getDomainCode()));
        meta.put("question_type", nullSafe(doc.getQuestionType()));
        meta.put("difficulty", nullSafe(doc.getDifficulty()));
        meta.put("keywords", List.copyOf(safeList(doc.getKeywords())));
        meta.put("source", nullSafe(doc.getSource()));
        meta.put("active", doc.isActive());
        meta.put("version", nullSafe(doc.getVersion()));
        meta.put("follow_up_ids", List.copyOf(safeList(doc.getFollowUpIds())));
        return meta;
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String nullSafe(String val) {
        return val != null ? val : "";
    }
}
