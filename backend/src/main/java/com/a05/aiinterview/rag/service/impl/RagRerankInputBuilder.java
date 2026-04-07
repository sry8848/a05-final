package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRerankService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 构造 rerank 阶段使用的 query/document 文本。
 *
 * <p>现行契约只保留语义视图：query 仅使用自然语言语义查询，不再混入题型、
 * difficultyHint、keywordHints 或 sparseQueryText 等检索元信息。
 */
public class RagRerankInputBuilder {

    public String buildQueryText(RagRetrievalRequest request) {
        if (request == null) {
            return "";
        }
        return firstNonBlank(request.getDenseQueryText(), request.getQueryText());
    }

    public String buildDocumentText(RagRerankService.RerankCandidate candidate) {
        if (candidate == null) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        addLine(lines, "题目", candidate.questionText());
        addLine(lines, "考点", candidate.intentConcept());
        addLine(lines, "语境", candidate.referenceContext());
        addJoinedLine(lines, "关键点", candidate.scoringKeyPoints());
        addJoinedLine(lines, "误区", candidate.scoringPitfalls());
        return String.join("\n", lines).trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private void addLine(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + "：" + value.trim());
        }
    }

    private void addJoinedLine(List<String> lines, String label, List<String> values) {
        if (values == null) {
            return;
        }
        List<String> normalized = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        if (!normalized.isEmpty()) {
            lines.add(label + "：" + String.join("；", normalized));
        }
    }
}
