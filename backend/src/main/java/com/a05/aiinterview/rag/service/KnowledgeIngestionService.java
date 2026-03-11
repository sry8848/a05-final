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
 * <p>负责将原始知识文档切片、标注 metadata，并批量写入 Qdrant VectorStore。
 * 向量化（Embedding）由 Spring AI 的 {@link VectorStore#add} 内部调用 EmbeddingModel 完成，
 * 业务层无需感知 Embedding 细节。
 *
 * <p>切片策略：按空行（段落）分割，合并至约 {@code CHUNK_SIZE} 字符为一个片段，
 * 保证语义完整性的同时控制每片大小适合向量化。
 *
 * <p>仅当 {@code rag.enabled=true} 时激活，避免在未接入 Qdrant 时注入失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class KnowledgeIngestionService {

    /** 每个文档片段的目标字符数（约 300~500 字，平衡语义完整与向量精度） */
    private static final int CHUNK_SIZE = 400;

    private final VectorStore vectorStore;
    private final RagProperties ragProperties;

    /**
     * 批量入库知识文档列表。
     *
     * @param documents 待入库的知识文档列表，每个文档可包含多个段落
     * @return 实际写入 Qdrant 的文档片段总数
     */
    public int ingest(List<KnowledgeDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("知识入库：文档列表为空，跳过");
            return 0;
        }

        log.info("知识入库开始, 文档数={}", documents.size());
        List<Document> springAiDocs = new ArrayList<>();

        for (KnowledgeDocument doc : documents) {
            List<String> chunks = splitIntoChunks(doc.getContent(), CHUNK_SIZE);
            Map<String, Object> metadata = buildMetadata(doc);
            for (String chunk : chunks) {
                springAiDocs.add(new Document(chunk, metadata));
            }
        }

        vectorStore.add(springAiDocs);
        log.info("知识入库完成, 原始文档数={}, 切片数={}", documents.size(), springAiDocs.size());
        return springAiDocs.size();
    }

    /**
     * 将单份文档内容按段落边界切片，合并到约 chunkSize 字符。
     * 若整个文档小于 chunkSize，则整体作为一片。
     *
     * @param text      原始文档正文
     * @param chunkSize 目标片段字符数
     * @return 切片后的文本片段列表
     */
    private List<String> splitIntoChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        // 按连续空行（段落）分割
        String[] paragraphs = text.split("\\n{2,}");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            // 当前缓冲已达到目标大小时，先将已积累内容作为一片
            if (current.length() > 0 && current.length() + trimmed.length() > chunkSize) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(trimmed).append("\n\n");
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }

        // 兜底：若段落无法切分则整体作为一片
        if (chunks.isEmpty()) {
            chunks.add(text.trim());
        }
        return chunks;
    }

    /**
     * 将 KnowledgeDocument 的字段映射为 Qdrant metadata 键值对。
     * metadata 字段是检索过滤的核心依据，命名规范须与 RagRetrievalServiceImpl 中的过滤表达式一致。
     *
     * @param doc 知识文档
     * @return 可直接传入 Spring AI Document 的 metadata Map
     */
    private Map<String, Object> buildMetadata(KnowledgeDocument doc) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("knowledge_type", nullSafe(doc.getKnowledgeType()));
        meta.put("domain_code", nullSafe(doc.getDomainCode()));
        meta.put("position_code", nullSafe(doc.getPositionCode()));
        meta.put("difficulty", nullSafe(doc.getDifficulty()));
        meta.put("source", nullSafe(doc.getSource()));
        if (doc.getQuestionType() != null) {
            meta.put("question_type", doc.getQuestionType());
        }
        if (doc.getVersion() != null) {
            meta.put("version", doc.getVersion());
        }
        return meta;
    }

    private String nullSafe(String val) {
        return val != null ? val : "";
    }
}
