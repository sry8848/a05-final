package com.a05.aiinterview.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG 检索结果上下文。
 *
 * <p>{@code contextText} 是聚合后可直接注入出题 Prompt 的字符串。
 * 空结果（无命中或降级）时 {@code empty=true}，{@code contextText} 为空字符串，
 * 出题链路按原逻辑继续运行，不受影响。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagContext {

    /** 聚合后可注入 Prompt 的知识片段文本；无命中时为空字符串 */
    private String contextText;

    /** 实际命中的文档片段数量 */
    private int hitCount;

    /** true 表示无命中或降级，出题时跳过注入 */
    private boolean empty;

    /**
     * 构建空结果（无命中或 rag.enabled=false）。
     *
     * @return 空 RagContext 实例
     */
    public static RagContext empty() {
        return RagContext.builder()
                .contextText("")
                .hitCount(0)
                .empty(true)
                .build();
    }

    /**
     * 将检索结果序列化为审计日志可存储的 Map 结构。
     *
     * @return 包含 contextText/hitCount/empty 的 Map
     */
    public Map<String, Object> toAuditMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hitCount", hitCount);
        map.put("empty", empty);
        if (!empty && contextText != null && !contextText.isBlank()) {
            // 审计日志只截取前 500 字符，避免日志过大
            map.put("contextPreview", contextText.length() > 500
                    ? contextText.substring(0, 500) + "..."
                    : contextText);
        }
        return map;
    }
}
