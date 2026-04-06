package com.a05.aiinterview.rag.service.impl;

import com.a05.aiinterview.rag.config.RagProperties;
import com.a05.aiinterview.rag.dto.RagRetrievalRequest;
import com.a05.aiinterview.rag.service.RagRerankService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 阿里百炼文本排序 API 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.enabled", havingValue = "true")
public class DashScopeRagRerankService implements RagRerankService {

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    @Override
    public List<RerankResult> rerank(RagRetrievalRequest request, List<RerankCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        RagProperties.RerankProperties rerank = ragProperties.getRerank();
        if (rerank.getApiKey() == null || rerank.getApiKey().isBlank()) {
            throw new IllegalStateException("rag.rerank.api-key 未配置，无法调用百炼文本排序 API");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(rerank.getTimeoutMs()))
                    .build();

            String requestBody = objectMapper.writeValueAsString(new DashScopeRerankRequest(
                    rerank.getModel(),
                    new DashScopeInput(
                            buildQueryBrief(request),
                            candidates.stream().map(this::toDocumentText).toList()
                    ),
                    Map.of(
                            "top_n", Math.min(rerank.getTopN(), candidates.size()),
                            "return_documents", false
                    )
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(rerank.getEndpoint()))
                    .timeout(Duration.ofMillis(rerank.getTimeoutMs()))
                    .header("Authorization", "Bearer " + rerank.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("百炼文本排序 API 调用失败, status=" + response.statusCode() + ", body=" + response.body());
            }

            DashScopeRerankResponse rerankResponse = objectMapper.readValue(response.body(), DashScopeRerankResponse.class);
            List<DashScopeResult> results = rerankResponse.output() == null ? List.of() : rerankResponse.output().results();
            if (results == null || results.isEmpty()) {
                return List.of();
            }

            List<RerankResult> rerankResults = new ArrayList<>();
            for (DashScopeResult result : results) {
                if (result.index() == null || result.index() < 0 || result.index() >= candidates.size()) {
                    continue;
                }
                rerankResults.add(new RerankResult(
                        candidates.get(result.index()).questionId(),
                        result.relevanceScore() == null ? 0.0d : result.relevanceScore()
                ));
            }
            rerankResults.sort(Comparator.comparingDouble(RerankResult::relevanceScore).reversed());
            return rerankResults;
        } catch (Exception e) {
            throw new IllegalStateException("调用百炼文本排序 API 失败", e);
        }
    }

    private String buildQueryBrief(RagRetrievalRequest request) {
        List<String> lines = new ArrayList<>();
        addLine(lines, "题型", request.getQuestionType());
        addLine(lines, "目标", request.getQueryText());
        addLine(lines, "焦点", request.getFocusPoint());
        addJoinedLine(lines, "关键词", request.getKeywordQueries());
        addLine(lines, "目标难度", request.getDifficultyHint());
        return String.join("\n", lines).trim();
    }

    private String toDocumentText(RerankCandidate candidate) {
        List<String> lines = new ArrayList<>();
        addLine(lines, "题目", candidate.questionText());
        addLine(lines, "考点", candidate.intentConcept());
        addLine(lines, "语境", candidate.referenceContext());
        addJoinedLine(lines, "关键点", candidate.scoringKeyPoints());
        addJoinedLine(lines, "误区", candidate.scoringPitfalls());
        return String.join("\n", lines).trim();
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

    private record DashScopeRerankRequest(
            String model,
            DashScopeInput input,
            Map<String, Object> parameters
    ) {
    }

    private record DashScopeInput(
            String query,
            List<String> documents
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DashScopeRerankResponse(
            DashScopeOutput output
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DashScopeOutput(
            List<DashScopeResult> results
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DashScopeResult(
            Integer index,
            @JsonProperty("relevance_score") Double relevanceScore
    ) {
    }
}
